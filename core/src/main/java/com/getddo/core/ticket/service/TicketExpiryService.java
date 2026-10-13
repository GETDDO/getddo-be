package com.getddo.core.ticket.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.Ticket;
import com.getddo.core.ticket.domain.TicketHistory;
import com.getddo.core.ticket.repository.TicketHistoryRepository;
import com.getddo.core.ticket.repository.TicketRepository;

/**
 * 만료 시각이 지난 응모권을 만료됨으로 바꾸고 만료 이력을 남기는 진입점.
 *
 * <p>만료 시각이 지나면 응모권은 조회와 차감에서 이미 쓸 수 없는 것으로 취급된다. 이 서비스는 그 상태를 응모권과 이력에
 * 기록해 보유 수량과 이력이 일치하게 한다. 이미 사용({@code SPENT})한 응모권은 만료하지 않아, 접수 완료된 응모는 월 경계
 * 뒤에도 유효하다.</p>
 *
 * <p>한 번의 호출은 독립된 트랜잭션 하나다. 대상은 다른 처리가 잠그지 않은 응모권만 잠가 읽으므로 차감 중인 응모권은 그
 * 처리가 끝난 뒤 다음 호출에서 만료되고, 서버 여러 대에서 동시에 호출해도 같은 응모권을 두 번 만료하지 않는다.
 * {@code READ COMMITTED}로 읽어 범위 잠금이 지급·차감을 막지 않게 한다.</p>
 */
@Service
@RequiredArgsConstructor
public class TicketExpiryService {

	private static final String EXPIRY_REASON = "응모권 만료";

	private final TicketRepository ticketRepository;
	private final TicketHistoryRepository historyRepository;
	private final TimeProvider timeProvider;

	/**
	 * 만료 시각이 지난 응모권을 최대 {@code limit}장 만료한다.
	 *
	 * @param limit 한 번에 처리할 최대 장수. 1 이상이어야 한다
	 * @return 이번에 만료한 응모권 수. 0이면 지금 처리할 응모권이 없다
	 * @throws IllegalArgumentException {@code limit}이 1 미만인 경우
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
	public int expireDue(int limit) {
		if (limit < 1) {
			throw new IllegalArgumentException("한 번에 만료할 최대 장수는 1 이상이어야 한다.");
		}
		// DATETIME(6)은 마이크로초 아래를 반올림해 저장하므로 미리 잘라 조회 기준과 기록 값이 어긋나지 않게 한다.
		Instant expiredAt = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
		List<Ticket> due = ticketRepository.findDueForExpiryForUpdate(expiredAt, limit);
		if (due.isEmpty()) {
			return 0;
		}
		List<Ticket> expired = due.stream().map(ticket -> ticket.expire(expiredAt)).toList();
		ticketRepository.updateAll(expired);
		historyRepository.saveAll(expired.stream().map(ticket -> TicketHistory.expire(ticket, EXPIRY_REASON)).toList());
		return expired.size();
	}
}
