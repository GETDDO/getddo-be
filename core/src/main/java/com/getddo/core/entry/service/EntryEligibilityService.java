package com.getddo.core.entry.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.entry.domain.EntryEligibility;
import com.getddo.core.entry.domain.EntryEvent;
import com.getddo.core.entry.domain.EntryParticipant;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.entry.repository.EntryEventReader;
import com.getddo.core.entry.repository.EntryParticipantRepository;
import com.getddo.core.ticket.domain.MyTickets;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.service.TicketQueryService;
import com.getddo.core.user.domain.Membership;

/**
 * 응모 자격 사전 조회(E07).
 *
 * <p>응모를 보장하지 않는다. 아무것도 잠그지 않고 읽기만 하며, 실제 응모({@link EntryService})에서 다시 검증한다.
 * 응모할 수 없는 사유는 응모 오류({@link EntryErrorCode})와 같은 이름으로 돌려준다.</p>
 */
@Service
@RequiredArgsConstructor
public class EntryEligibilityService {

	/** 응모권 보유가 모자란 사유. 응모 때는 응모권 도메인의 {@code TICKET-006}으로 나온다. */
	static final String INSUFFICIENT_TICKETS = "INSUFFICIENT_TICKETS";

	private final EntryEventReader eventReader;
	private final EntryParticipantRepository participantRepository;
	private final TicketQueryService ticketQueryService;
	private final TimeProvider timeProvider;

	/**
	 * 사용자가 이벤트에 응모할 수 있는지와 그 사유, 사용·잔여 수량을 조회한다.
	 *
	 * @param membership 요청 사용자의 멤버십. 관리자는 없을 수 있다
	 * @throws EntryException 이벤트가 없거나 삭제된 경우({@code EVENT_NOT_FOUND})
	 */
	@Transactional(readOnly = true)
	public EntryEligibility check(UUID userId, boolean admin, Membership membership, UUID eventId) {
		EntryEvent event = eventReader.find(eventId)
				.filter(found -> !found.isDeleted())
				.orElseThrow(() -> new EntryException(EntryErrorCode.EVENT_NOT_FOUND));
		Instant serverTime = timeProvider.now();
		EntryParticipant participant = participantRepository.find(eventId, userId).orElse(null);
		long used = participant == null ? 0 : participant.getUsedTicketCount();
		Long limit = event.ticketLimit();
		Long remaining = limit == null ? null : Math.max(limit - used, 0);
		long available = availableTickets(event, userId);

		List<String> reasons = new ArrayList<>();
		if (admin) {
			reasons.add(EntryErrorCode.ADMIN_ENTRY_FORBIDDEN.name());
		}
		if (!event.allows(membership)) {
			reasons.add(EntryErrorCode.ENTRY_MEMBERSHIP_NOT_MET.name());
		}
		if (!event.isOpenAt(serverTime)) {
			reasons.add(EntryErrorCode.EVENT_NOT_OPEN.name());
		}
		addQuantityReasons(reasons, event, participant != null, remaining, available);
		return new EntryEligibility(eventId, reasons, used, remaining, available, serverTime);
	}

	/** 이 이벤트에서 실제로 쓸 수 있는 보유 장수. 응모권을 쓰지 않으면 0, 가중치를 적용하지 않으면 브론즈 장수다. */
	private long availableTickets(EntryEvent event, UUID userId) {
		if (!event.usesTickets()) {
			return 0;
		}
		MyTickets mine = ticketQueryService.getMyTickets(userId);
		return event.isUnweighted() ? mine.getCountByGrade().get(TicketGrade.BRONZE) : mine.getAvailableCount();
	}

	private static void addQuantityReasons(List<String> reasons, EntryEvent event, boolean alreadyParticipant,
			Long remaining, long available) {
		boolean singleEntry = !event.usesTickets() || event.isUnweighted();
		if (singleEntry && alreadyParticipant) {
			reasons.add(EntryErrorCode.ALREADY_ENTERED.name());
			return;
		}
		if (!singleEntry && remaining != null && remaining == 0) {
			reasons.add(EntryErrorCode.TICKET_LIMIT_EXCEEDED.name());
			return;
		}
		if (event.usesTickets() && available == 0) {
			reasons.add(event.isUnweighted() ? EntryErrorCode.BRONZE_REQUIRED.name() : INSUFFICIENT_TICKETS);
		}
	}
}
