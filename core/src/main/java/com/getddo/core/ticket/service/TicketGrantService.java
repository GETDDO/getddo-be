package com.getddo.core.ticket.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.GrantCommand;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceClaim;
import com.getddo.core.ticket.domain.TicketLedger;
import com.getddo.core.ticket.domain.TicketLedgerAllocation;
import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.core.ticket.domain.TicketWalletPeriod;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.repository.GrantSourceRepository;
import com.getddo.core.ticket.repository.TicketLedgerAllocationRepository;
import com.getddo.core.ticket.repository.TicketLedgerRepository;
import com.getddo.core.ticket.repository.TicketWalletRepository;

/**
 * 미션·출석·게임 보상이 응모권을 지급할 때 호출하는 단일 진입점.
 *
 * <p>응모권 담당이 지갑·원장·배분·만료 계산을 소유하며, 호출자는 지갑과 원장을 직접 읽거나 쓰지 않는다.
 * 이 서비스는 지급(GRANT)만 다룬다.</p>
 *
 * <h2>호출 규약</h2>
 * <ol>
 *   <li>호출 순서는 <b>기존 청구 조회 → (없으면) 청구 INSERT → {@link #grant}</b>다.
 *       기존 청구가 있으면 {@code grant} 대신 {@link #findGrant}로 기존 결과를 반환한다.</li>
 *   <li>청구 INSERT와 지급은 한 트랜잭션이어야 한다. 그래야 보상 청구 기록과 응모권 지급 결과가 일치한다.</li>
 *   <li>청구 테이블의 UNIQUE는 동시 요청이 겹친 경우의 안전망이다. 위반이 나면 그 트랜잭션은
 *       rollback-only이고 영속성 컨텍스트도 믿을 수 없으므로, 같은 트랜잭션에서 {@code findGrant}를
 *       잇지 않는다. 트랜잭션 전체를 롤백하고 새 트랜잭션에서 처음부터 다시 처리한다.</li>
 *   <li>청구 UNIQUE 위반은 청구 저장 시점이나 {@code grant} 호출 중에 나올 수 있다. JPA는 청구 INSERT를
 *       flush 때까지 미루고, {@code grant}는 방금 저장한 청구를 읽으려고 먼저 flush하기 때문이다.
 *       어느 쪽이든 트랜잭션 전체를 롤백하고 재처리한다.</li>
 *   <li>이 서비스가 던진 예외는 삼키지 않고 전파한다. 삼키고 진행하면 청구는 있고 원장은 없는 상태가 된다.</li>
 *   <li>트랜잭션 설정은 프록시를 통해서만 적용되므로 다른 Bean에서 호출한다.</li>
 * </ol>
 */
@Service
public class TicketGrantService {

	private final GrantSourceRepository grantSourceRepository;
	private final TicketLedgerRepository ledgerRepository;
	private final TicketWalletRepository walletRepository;
	private final TicketLedgerAllocationRepository allocationRepository;
	private final TimeProvider timeProvider;

	public TicketGrantService(
			GrantSourceRepository grantSourceRepository,
			TicketLedgerRepository ledgerRepository,
			TicketWalletRepository walletRepository,
			TicketLedgerAllocationRepository allocationRepository,
			TimeProvider timeProvider) {
		this.grantSourceRepository = grantSourceRepository;
		this.ledgerRepository = ledgerRepository;
		this.walletRepository = walletRepository;
		this.allocationRepository = allocationRepository;
		this.timeProvider = timeProvider;
	}

	/**
	 * 청구 한 건에 대해 응모권을 지급한다.
	 *
	 * <p>호출자 트랜잭션에 참여하며 자체적으로 커밋하지 않는다. 트랜잭션 없이 호출하면
	 * {@code MANDATORY} 설정에 따라 실패한다.</p>
	 *
	 * <p>지급 시각은 서비스가 한 번 구하고, 그 시각의 KST 월로 지갑과 만료 시각을 정한다.
	 * 같은 청구로 다시 호출하면 추가로 지급하지 않고 기존 결과를 {@code replayed=true}로 반환한다.</p>
	 *
	 * @param command 지급 요청
	 * @return 이번에 확정된 지급 결과, 또는 이미 확정된 지급 결과
	 * @throws com.getddo.core.common.exception.BusinessException 입력이 올바르지 않거나
	 *         청구가 없거나 청구의 사용자·수량이 요청과 다른 경우
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public GrantResult grant(GrantCommand command) {
		validate(command);
		GrantSource source = command.getSource();
		verifyClaim(command);

		// 같은 청구의 지급 원장이 있으면 재호출이다. 지갑을 건드리지 않고 확정된 결과를 돌려준다.
		Optional<TicketLedger> granted = ledgerRepository.findByIdempotencyKey(source.idempotencyKey());
		if (granted.isPresent()) {
			return granted.get().toGrantResult(true);
		}

		// 지급 시각은 한 번만 구한다. 지갑 월·만료 시각과 지갑·원장·배분의 생성 시각이 모두 이 값을 쓴다.
		// DATETIME(6)은 마이크로초 아래를 반올림해 저장하므로 미리 잘라, 응답·재조회·저장 값과 지갑 월이 어긋나지 않게 한다.
		Instant grantedAt = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
		TicketWalletPeriod period = TicketWalletPeriod.forGrant(grantedAt, timeProvider);
		TicketWallet deposited = walletRepository
				.getOrCreateForUpdate(command.getUserId(), period, grantedAt)
				.deposit(command.getQuantity());
		walletRepository.save(deposited);

		TicketLedger ledger = ledgerRepository.save(TicketLedger.grant(deposited, command, grantedAt));
		allocationRepository.save(TicketLedgerAllocation.selfCredit(ledger));
		return ledger.toGrantResult(false);
	}

	/**
	 * 이미 지급된 청구의 결과를 조회한다.
	 *
	 * <p>기존 청구가 있어 {@code grant}를 호출하지 않는 경우에 기존 결과를 돌려줄 때 쓴다.
	 * 기존 트랜잭션이 있으면 참여하고, 없으면 읽기 전용 트랜잭션을 연다.</p>
	 *
	 * <p>청구 소유자는 확인하지 않는다. 청구가 요청 사용자의 것인지는 호출자가 확인하며, 사용자 입력으로 받은 청구 ID를
	 * 그대로 넘기지 않는다.</p>
	 *
	 * @param source 조회할 청구
	 * @return 지급 결과({@code replayed=true}). 아직 지급되지 않았으면 빈 값
	 */
	@Transactional(readOnly = true)
	public Optional<GrantResult> findGrant(GrantSource source) {
		if (!isComplete(source)) {
			throw new BusinessException(TicketErrorCode.TICKET_INVALID_GRANT);
		}
		return ledgerRepository.findByIdempotencyKey(source.idempotencyKey())
				.map(ledger -> ledger.toGrantResult(true));
	}

	private static void validate(GrantCommand command) {
		if (command == null
				|| command.getUserId() == null
				|| !isComplete(command.getSource())
				|| command.getQuantity() < 1
				|| command.getReason() == null
				|| command.getReason().isBlank()) {
			throw new BusinessException(TicketErrorCode.TICKET_INVALID_GRANT);
		}
	}

	private static boolean isComplete(GrantSource source) {
		return source != null && source.getType() != null && source.getClaimId() != null;
	}

	/** 청구 행이 있고 사용자·수량이 요청과 같은지 확인한다. 사용자 존재는 청구의 FK가 보장한다. */
	private void verifyClaim(GrantCommand command) {
		GrantSourceClaim claim = grantSourceRepository.find(command.getSource())
				.orElseThrow(() -> new BusinessException(TicketErrorCode.TICKET_GRANT_SOURCE_NOT_FOUND));
		if (!claim.getUserId().equals(command.getUserId()) || claim.getTicketCount() != command.getQuantity()) {
			throw new BusinessException(TicketErrorCode.TICKET_GRANT_SOURCE_MISMATCH);
		}
	}
}
