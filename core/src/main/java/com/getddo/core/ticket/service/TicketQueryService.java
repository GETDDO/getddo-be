package com.getddo.core.ticket.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.pagination.CursorQuery;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.MyTicketWallets;
import com.getddo.core.ticket.domain.TicketLedgerCursor;
import com.getddo.core.ticket.domain.TicketLedgerFilter;
import com.getddo.core.ticket.domain.TicketTransactionView;
import com.getddo.core.ticket.domain.TicketWalletView;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.TicketQueryRepository;

/**
 * 사용자의 응모권 잔액(T01)과 이력(T02)을 조회한다.
 *
 * <p>조회 대상 사용자는 호출자가 확인한 요청 사용자여야 한다. 이 서비스는 사용자 ID를 그대로 믿고 조회한다.</p>
 */
@Service
@RequiredArgsConstructor
public class TicketQueryService {

	/** 이력 한 번 조회의 최대 개수. 공통 API 계약의 커서 조회 상한이다. */
	private static final int MAX_PAGE_SIZE = 100;

	private final TicketQueryRepository queryRepository;
	private final TimeProvider timeProvider;

	/**
	 * 사용자의 지갑 목록과 사용 가능 잔액을 조회한다.
	 *
	 * <p>조회 시각을 한 번 구해 지갑 상태와 사용 가능 잔액을 모두 그 시각 기준으로 계산한다. 만료 처리가 늦어져 아직
	 * ACTIVE로 저장된 지갑도 만료 시각이 지났으면 만료로 보여 주고 사용 가능 잔액에서 뺀다.</p>
	 *
	 * @param userId 요청 사용자 ID
	 * @return 만료월 최신순 지갑 목록, 사용 가능 잔액, 조회 시각
	 */
	@Transactional(readOnly = true)
	public MyTicketWallets getMyWallets(UUID userId) {
		Objects.requireNonNull(userId, "userId");
		Instant serverTime = timeProvider.now();
		List<TicketWalletView> wallets = queryRepository.findWallets(userId).stream()
				.map(wallet -> TicketWalletView.of(wallet, serverTime))
				.toList();
		return MyTicketWallets.of(wallets, serverTime);
	}

	/**
	 * 사용자의 응모권 이력을 커서로 조회한다.
	 *
	 * <p>정렬은 {@code createdAt DESC, id DESC}로 고정한다. 생성 시각이 같은 이력이 있어도 ID로 순서가 정해지므로
	 * 페이지를 넘길 때 중복·누락이 없다.</p>
	 *
	 * @param userId 요청 사용자 ID
	 * @param filter 거래 유형·기간 조건
	 * @param page   커서와 조회 개수(1~100)
	 * @return 이력, 다음 커서(마지막이면 null), 조건에 맞는 전체 수
	 * @throws TicketException 커서 형식·조회 개수·기간 조건이 올바르지 않은 경우
	 */
	@Transactional(readOnly = true)
	public CursorResult<TicketTransactionView> getMyLedger(UUID userId, TicketLedgerFilter filter,
			CursorQuery page) {
		Objects.requireNonNull(userId, "userId");
		validate(filter, page);
		TicketLedgerCursor after = page.getCursor() == null ? null : TicketLedgerCursor.decode(page.getCursor());

		// 다음 페이지가 있는지 알려고 한 건 더 조회한다.
		List<TicketTransactionView> rows = queryRepository.findLedger(userId, filter, after, page.getSize() + 1);
		boolean hasNext = rows.size() > page.getSize();
		List<TicketTransactionView> items = hasNext ? rows.subList(0, page.getSize()) : rows;
		String nextCursor = hasNext ? items.get(items.size() - 1).cursor().encode() : null;
		return new CursorResult<>(items, nextCursor, queryRepository.countLedger(userId, filter));
	}

	/**
	 * HTTP 요청 값 그대로 이력을 조회한다.
	 *
	 * <p>{@link CursorQuery}는 빈 커서나 1 미만의 개수를 {@link IllegalArgumentException}으로 거절하므로, 만들기 전에
	 * 같은 조건을 조회 조건 오류로 바꾼다.</p>
	 *
	 * @param userId 요청 사용자 ID
	 * @param filter 거래 유형·기간 조건
	 * @param cursor 이전 응답의 다음 커서. 첫 조회면 null
	 * @param size   조회 개수(1~100)
	 * @return 이력, 다음 커서(마지막이면 null), 조건에 맞는 전체 수
	 * @throws TicketException 커서 형식·조회 개수·기간 조건이 올바르지 않은 경우
	 */
	@Transactional(readOnly = true)
	public CursorResult<TicketTransactionView> getMyLedger(UUID userId, TicketLedgerFilter filter, String cursor,
			int size) {
		if (size < 1 || (cursor != null && cursor.isBlank())) {
			throw new TicketException(TicketErrorCode.TICKET_INVALID_LEDGER_QUERY);
		}
		return getMyLedger(userId, filter, new CursorQuery(cursor, size));
	}

	private static void validate(TicketLedgerFilter filter, CursorQuery page) {
		if (filter == null || page == null || page.getSize() > MAX_PAGE_SIZE || !filter.hasValidPeriod()) {
			throw new TicketException(TicketErrorCode.TICKET_INVALID_LEDGER_QUERY);
		}
	}
}
