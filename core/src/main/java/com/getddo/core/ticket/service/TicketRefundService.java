package com.getddo.core.ticket.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.RefundCommand;
import com.getddo.core.ticket.domain.RefundResult;
import com.getddo.core.ticket.domain.Ticket;
import com.getddo.core.ticket.domain.TicketExpiry;
import com.getddo.core.ticket.domain.TicketHistory;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.domain.UsedTicket;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.TicketHistoryRepository;
import com.getddo.core.ticket.repository.TicketRepository;

/**
 * 이벤트 취소·참여 제외처럼 응모가 무효가 될 때 그 응모에 쓴 응모권을 모두 되돌리는 진입점.
 *
 * <p>같은 응모권이 {@code RETURNED} 상태로 돌아오며 등급은 그대로다. 새 응모권을 지급하는 것이 아니고 등급은 지급 때
 * 한 번 정해져 바뀌지 않기 때문이다(공용 스펙 {@code 02-domain/ticket.md}, {@code 00-requirements/pending-decisions.md}).
 * 반환 후 만료 시각은 반환한 KST 월의 다음 달 말이다. 응모 상태 변경과 반환은 같은 트랜잭션에서 호출해 함께 커밋되거나 함께 롤백돼야 한다.
 * 이 서비스가 던진 예외는 삼키지 않고 전파한다.</p>
 */
@Service
@RequiredArgsConstructor
public class TicketRefundService {

	private final TicketRepository ticketRepository;
	private final TicketHistoryRepository historyRepository;
	private final TimeProvider timeProvider;

	/**
	 * 응모 한 건에 쓴 응모권을 모두 반환한다.
	 *
	 * <p>호출자 트랜잭션에 참여하며 자체적으로 커밋하지 않는다. 같은 응모로 다시 호출하면 추가로 반환하지 않고
	 * 기존 결과를 {@code replayed=true}로 반환한다. 같은 응모의 반환이 동시에 오면 뒤 요청은 응모권 잠금에서
	 * 기다렸다가 앞 요청의 반환 이력을 보고 같은 결과를 돌려받는다.</p>
	 *
	 * @param command 반환 요청
	 * @return 이번에 확정된 반환 결과, 또는 이미 확정된 반환 결과
	 * @throws TicketException 입력이 올바르지 않거나, 응모에 사용 내역이 없거나,
	 *         응모권의 상태가 사용 직후 상태와 다른 경우
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public RefundResult refund(RefundCommand command) {
		validate(command);

		List<TicketHistory> uses = historyRepository.findUseHistories(command.getEventEntryId());
		if (uses.isEmpty()) {
			throw new TicketException(TicketErrorCode.TICKET_USE_NOT_FOUND);
		}
		List<UUID> useIds = uses.stream().map(TicketHistory::getId).toList();
		List<TicketHistory> refunded = historyRepository.findRefundsOf(useIds);
		if (refunded.size() == uses.size()) {
			return replayOf(refunded);
		}

		// 잠금을 기다리는 동안 같은 응모의 앞 반환이 커밋했을 수 있다. 일반 조회는 오래된 스냅샷을 볼 수 있으므로 이력이
		// 아니라 잠금으로 읽은 최신 응모권 상태로 판정한다.
		Map<UUID, Ticket> ticketsById = ticketRepository
				.findAllForUpdate(uses.stream().map(TicketHistory::getTicketId).toList()).stream()
				.collect(Collectors.toMap(Ticket::getId, Function.identity()));
		List<TicketHistory> pending = new ArrayList<>();
		for (TicketHistory use : uses) {
			switch (stateAfter(use, ticketsById.get(use.getTicketId()))) {
				case SPENT_AS_USED -> pending.add(use);
				case ALREADY_REFUNDED -> { }
				case CHANGED -> throw new TicketException(TicketErrorCode.TICKET_REFUND_STATE_MISMATCH);
			}
		}
		if (pending.isEmpty()) {
			return replayOfTickets(uses, ticketsById);
		}

		Instant refundedAt = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
		Instant expiresAt = TicketExpiry.forRefund(refundedAt, timeProvider);
		List<Ticket> returned = new ArrayList<>();
		List<TicketHistory> histories = new ArrayList<>();
		for (TicketHistory use : pending) {
			Ticket ticket = ticketsById.get(use.getTicketId()).refund(refundedAt, expiresAt);
			returned.add(ticket);
			histories.add(TicketHistory.refund(ticket, use.getId(), command.getReason()));
		}
		ticketRepository.updateAll(returned);
		historyRepository.saveAll(histories);
		return new RefundResult(
				returned.stream().map(ticket -> new UsedTicket(ticket.getId(), ticket.getGrade())).toList(),
				refundedAt, expiresAt, false);
	}

	private enum TicketState {
		/** 사용 직후 상태 그대로다. 반환할 수 있다. */
		SPENT_AS_USED,
		/** 이 사용을 되돌리는 반환이 이미 반영됐다. */
		ALREADY_REFUNDED,
		/** 다른 처리가 끼어들어 이 사용과 이어지지 않는다. */
		CHANGED
	}

	/**
	 * 잠가 읽은 응모권이 이 사용 이력의 직후 상태인지, 이미 이 사용을 반환한 상태인지 가린다. 사용됨에서 반환됨으로 바꾸는
	 * 처리는 반환뿐이므로, 반환됨이고 버전이 사용 버전 + 1이면 이 사용을 되돌린 것이다.
	 */
	private static TicketState stateAfter(TicketHistory use, Ticket ticket) {
		if (ticket == null) {
			return TicketState.CHANGED;
		}
		if (ticket.getStatus() == TicketStatus.SPENT && ticket.getVersion() == use.getTicketVersion()) {
			return TicketState.SPENT_AS_USED;
		}
		if (ticket.getStatus() == TicketStatus.RETURNED && ticket.getVersion() == use.getTicketVersion() + 1) {
			return TicketState.ALREADY_REFUNDED;
		}
		return TicketState.CHANGED;
	}

	/** 앞선 반환이 이미 반영된 응모권의 현재 값으로 기존 결과를 만든다. 반환 시각과 만료 시각은 응모권의 현재 값이다. */
	private static RefundResult replayOfTickets(List<TicketHistory> uses, Map<UUID, Ticket> ticketsById) {
		Ticket first = ticketsById.get(uses.get(0).getTicketId());
		return new RefundResult(uses.stream()
				.map(use -> ticketsById.get(use.getTicketId()))
				.map(ticket -> new UsedTicket(ticket.getId(), ticket.getGrade()))
				.toList(), first.getUpdatedAt(), first.getExpiresAt(), true);
	}

	private RefundResult replayOf(List<TicketHistory> refunds) {
		Map<UUID, Ticket> tickets = ticketRepository
				.findAllByIds(refunds.stream().map(TicketHistory::getTicketId).toList()).stream()
				.collect(Collectors.toMap(Ticket::getId, Function.identity()));
		TicketHistory first = refunds.get(0);
		return new RefundResult(refunds.stream()
				.map(refund -> new UsedTicket(refund.getTicketId(), tickets.get(refund.getTicketId()).getGrade()))
				.toList(), first.getCreatedAt(), first.getExpiresAt(), true);
	}

	private static void validate(RefundCommand command) {
		if (command == null
				|| command.getEventEntryId() == null
				|| command.getReason() == null
				|| command.getReason().isBlank()) {
			throw new TicketException(TicketErrorCode.TICKET_INVALID_USE);
		}
	}
}
