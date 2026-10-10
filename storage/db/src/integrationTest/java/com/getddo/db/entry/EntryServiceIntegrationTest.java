package com.getddo.db.entry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.entry.domain.EntryCommand;
import com.getddo.core.entry.domain.EntryReceipt;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.user.domain.Membership;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 MySQL에서 응모 한 건이 응모·응모자·응모권·차감 이력에 남기는 결과와 응모 규칙을 검증한다. */
class EntryServiceIntegrationTest extends EntryIntegrationTestSupport {

	@Test
	@DisplayName("고른 등급별 장수만큼 차감하고 응모자·응모·차감 이력이 서로 맞게 남는다")
	void acceptsWeightedEntryAndDeductsPerGrade() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 3);
		giveTickets(userId, TicketGrade.SILVER, 2);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		// when
		EntryReceipt receipt = entryService.enter(command(userId, event, entry,
				tickets(TicketGrade.GOLD, 2, TicketGrade.SILVER, 1)));
		// then
		assertThat(receipt.isCreated()).isTrue();
		assertThat(receipt.getEntryId()).isEqualTo(entry);
		assertThat(receipt.getTicketCount()).isEqualTo(3);
		assertThat(receipt.getTicketsByGrade()).containsEntry(TicketGrade.GOLD, 2L)
				.containsEntry(TicketGrade.SILVER, 1L).containsEntry(TicketGrade.BRONZE, 0L);
		assertThat(count("select count(*) from event_entries where id = ? and requested_ticket_count = 3 "
				+ "and deducted_ticket_count = 3", bytes(entry))).isEqualTo(1);
		assertThat(usedTicketCount(userId, event)).isEqualTo(3);
		assertThat(spentTickets(userId)).isEqualTo(3);
		assertThat(count("select count(*) from ticket_histories where event_entry_id = ? and operation_type = 'USE'",
				bytes(entry))).isEqualTo(3);
		assertThat(ticketQueryService.getMyTickets(userId).getCountByGrade())
				.containsEntry(TicketGrade.GOLD, 1L).containsEntry(TicketGrade.SILVER, 1L);
	}

	@Test
	@DisplayName("응모권 미사용 이벤트는 빈 요청으로 한 번만 응모할 수 있고 차감 없이 응모 0/0이 남는다")
	void noTicketEventAcceptsEmptyRequestOnce() {
		// given
		UUID event = noTicketEvent();
		UUID entry = UUID.randomUUID();
		// when
		EntryReceipt receipt = entryService.enter(command(userId, event, entry, tickets()));
		// then
		assertThat(receipt.getTicketCount()).isZero();
		assertThat(count("select count(*) from event_entries where id = ? and requested_ticket_count = 0 "
				+ "and deducted_ticket_count = 0", bytes(entry))).isEqualTo(1);
		assertThat(usedTicketCount(userId, event)).isZero();
		assertThatThrownBy(() -> entryService.enter(command(userId, event, UUID.randomUUID(), tickets())))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.ALREADY_ENTERED));
		assertThatThrownBy(() -> entryService.enter(command(seeds.user(), event, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.INVALID_ENTRY_REQUEST));
	}

	@Test
	@DisplayName("가중치 미적용 이벤트는 브론즈 1장만 쓸 수 있고 한 번만 응모할 수 있다")
	void unweightedEventAcceptsOneBronzeOnce() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 2);
		giveTickets(userId, TicketGrade.GOLD, 1);
		UUID event = unweightedEvent();
		// when
		EntryReceipt receipt = entryService.enter(command(userId, event, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1)));
		// then
		assertThat(receipt.getTicketsByGrade()).containsEntry(TicketGrade.BRONZE, 1L);
		assertThat(usedTicketCount(userId, event)).isEqualTo(1);
		assertThatThrownBy(() -> entryService.enter(command(userId, event, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.ALREADY_ENTERED));
		assertThat(spentTickets(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("가중치 미적용 이벤트에 브론즈 외 등급이나 1장이 아닌 요청을 보내면 요청 형식 오류다")
	void unweightedEventRejectsOtherGradesAndQuantities() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 3);
		giveTickets(userId, TicketGrade.GOLD, 1);
		UUID event = unweightedEvent();
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, UUID.randomUUID(),
				tickets(TicketGrade.GOLD, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.INVALID_ENTRY_REQUEST));
		assertThatThrownBy(() -> entryService.enter(command(userId, event, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 2))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.INVALID_ENTRY_REQUEST));
		assertThatThrownBy(() -> entryService.enter(command(userId, event, UUID.randomUUID(), tickets())))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.INVALID_ENTRY_REQUEST));
		assertThat(spentTickets(userId)).isZero();
	}

	@Test
	@DisplayName("가중치 미적용 이벤트에서 사용 가능한 브론즈가 없으면 다른 등급이 있어도 BRONZE_REQUIRED이고 아무것도 남지 않는다")
	void unweightedEventWithoutBronzeFails() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 2);
		UUID event = unweightedEvent();
		UUID entry = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.BRONZE_REQUIRED));
		assertThat(count("select count(*) from event_entries where id = ?", bytes(entry))).isZero();
		assertThat(count("select count(*) from event_participants where user_id = ?", bytes(userId))).isZero();
		assertThat(spentTickets(userId)).isZero();
	}

	@Test
	@DisplayName("일반 가중치 이벤트는 누적 5장까지 추가 응모할 수 있고 초과하면 TICKET_LIMIT_EXCEEDED이며 정확히 5장은 허용한다")
	void weightedEventEnforcesCumulativeLimit() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 10);
		UUID event = weightedEvent(5);
		// when
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 3)));
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 3))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		assertThat(usedTicketCount(userId, event)).isEqualTo(3);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 2)));
		assertThat(usedTicketCount(userId, event)).isEqualTo(5);
		assertThatThrownBy(() -> entryService.enter(command(userId, event, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		assertThat(spentTickets(userId)).isEqualTo(5);
		assertThat(count("select count(*) from event_participants where user_id = ? and event_id = ?",
				bytes(userId), bytes(event))).isEqualTo(1);
	}

	@Test
	@DisplayName("월말 소진용 이벤트는 수량 상한 없이 보유 범위에서 응모할 수 있다")
	void monthEndEventHasNoCumulativeLimit() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 8);
		UUID event = weightedEvent(null);
		// when
		EntryReceipt receipt = entryService.enter(command(userId, event, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 8)));
		// then
		assertThat(receipt.getTicketCount()).isEqualTo(8);
		assertThat(usedTicketCount(userId, event)).isEqualTo(8);
	}

	@Test
	@DisplayName("고른 등급의 응모권이 부족하면 TICKET-006으로 실패하고 응모자·응모·차감이 하나도 남지 않는다")
	void insufficientTicketsRollBackEverything() {
		// given
		giveTickets(userId, TicketGrade.SILVER, 1);
		giveTickets(userId, TicketGrade.BRONZE, 2);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry,
				tickets(TicketGrade.BRONZE, 2, TicketGrade.SILVER, 2))))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INSUFFICIENT));
		assertThat(count("select count(*) from event_entries where id = ?", bytes(entry))).isZero();
		assertThat(count("select count(*) from event_participants where user_id = ?", bytes(userId))).isZero();
		assertThat(spentTickets(userId)).isZero();
		assertThat(count("select count(*) from ticket_histories where operation_type = 'USE' "
				+ "and event_entry_id is not null and event_entry_id = ?", bytes(entry))).isZero();
	}

	@Test
	@DisplayName("같은 응모 ID로 같은 요청을 다시 보내면 추가 차감 없이 기존 결과를 created=false로 돌려준다")
	void sameEntryIdWithSameRequestReplaysExistingResult() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 2);
		giveTickets(userId, TicketGrade.SILVER, 2);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		Map<TicketGrade, Long> request = tickets(TicketGrade.GOLD, 1, TicketGrade.SILVER, 2);
		EntryReceipt first = entryService.enter(command(userId, event, entry, request));
		// when
		EntryReceipt again = entryService.enter(command(userId, event, entry, request));
		// then
		assertThat(again.isCreated()).isFalse();
		assertThat(again.getEntryId()).isEqualTo(entry);
		assertThat(again.getAcceptedAt()).isEqualTo(first.getAcceptedAt());
		assertThat(again.getTicketsByGrade()).isEqualTo(first.getTicketsByGrade());
		assertThat(spentTickets(userId)).isEqualTo(3);
		assertThat(usedTicketCount(userId, event)).isEqualTo(3);
		assertThat(count("select count(*) from event_entries where id = ?", bytes(entry))).isEqualTo(1);
	}

	@Test
	@DisplayName("모집이 끝난 뒤에도 이미 접수된 응모 ID의 재요청은 기존 결과를 돌려준다")
	void replayAfterEventEndedStillReturnsExistingResult() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 2);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 2)));
		clock.set(NOW.plusSeconds(3 * 86_400));
		// when
		EntryReceipt again = entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 2)));
		// then
		assertThat(again.isCreated()).isFalse();
		assertThat(spentTickets(userId)).isEqualTo(2);
	}

	@Test
	@DisplayName("같은 응모 ID로 다른 응모권 구성·수량이나 다른 사용자·이벤트 요청이 오면 IDEMPOTENCY_CONFLICT이고 추가로 바뀌는 것이 없다")
	void sameEntryIdWithDifferentRequestConflicts() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 2);
		giveTickets(userId, TicketGrade.SILVER, 2);
		UUID event = weightedEvent(5);
		UUID otherEvent = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		entryService.enter(command(userId, event, entry, tickets(TicketGrade.GOLD, 1, TicketGrade.SILVER, 1)));
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.GOLD, 2))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.IDEMPOTENCY_CONFLICT));
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry,
				tickets(TicketGrade.GOLD, 2, TicketGrade.SILVER, 0))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.IDEMPOTENCY_CONFLICT));
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry,
				tickets(TicketGrade.SILVER, 2))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.IDEMPOTENCY_CONFLICT));
		assertThatThrownBy(() -> entryService.enter(command(userId, otherEvent, entry,
				tickets(TicketGrade.GOLD, 1, TicketGrade.SILVER, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.IDEMPOTENCY_CONFLICT));
		assertThatThrownBy(() -> entryService.enter(command(seeds.user(), event, entry,
				tickets(TicketGrade.GOLD, 1, TicketGrade.SILVER, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.IDEMPOTENCY_CONFLICT));
		assertThat(spentTickets(userId)).isEqualTo(2);
		assertThat(usedTicketCount(userId, event)).isEqualTo(2);
	}

	@Test
	@DisplayName("응모가 반환된 뒤에 같은 응모 ID로 다시 요청해도 새로 차감하지 않고 접수된 기존 결과를 돌려준다")
	void replayAfterRefundDoesNotDeductAgain() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 3);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 2)));
		transaction.execute(status -> ticketRefundService.refund(
				new com.getddo.core.ticket.domain.RefundCommand(entry, "이벤트 취소")));
		long returned = count("select count(*) from tickets where user_id = ? and status = 'RETURNED'", bytes(userId));
		// when
		EntryReceipt again = entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 2)));
		// then
		assertThat(returned).isEqualTo(2);
		assertThat(again.isCreated()).isFalse();
		assertThat(again.getTicketsByGrade()).containsEntry(TicketGrade.BRONZE, 2L);
		assertThat(count("select count(*) from tickets where user_id = ? and status = 'RETURNED'", bytes(userId)))
				.isEqualTo(2);
		assertThat(spentTickets(userId)).isZero();
	}

	@Test
	@DisplayName("마감 시각 정각부터는 응모할 수 없고 그 직전 마이크로초까지는 응모할 수 있다")
	void entryClosesExactlyAtEndsAt() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 2);
		Instant endsAt = NOW.plusSeconds(3_600);
		UUID event = seeds.event("TICKET", true, 5, "excellent", NOW.minusSeconds(3_600), endsAt, "OPEN", null);
		// when
		// then
		clock.set(endsAt);
		assertThatThrownBy(() -> entryService.enter(command(userId, event, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_OPEN));
		clock.set(endsAt.minusNanos(1_000));
		assertThat(entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 1)))
				.isCreated()).isTrue();
	}

	@Test
	@DisplayName("시작 전이거나 취소·종료·삭제된 이벤트에는 응모할 수 없다")
	void closedCanceledOrDeletedEventsRejectEntry() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 3);
		UUID notStarted = seeds.event("TICKET", true, 5, "excellent", NOW.plusSeconds(3_600), NOW.plusSeconds(7_200),
				"SCHEDULED", null);
		UUID canceled = seeds.event("TICKET", true, 5, "excellent", NOW.minusSeconds(3_600), NOW.plusSeconds(7_200),
				"CANCELED", null);
		UUID drawn = seeds.event("TICKET", true, 5, "excellent", NOW.minusSeconds(7_200), NOW.plusSeconds(7_200),
				"DRAW_CONFIRMED", null);
		UUID deleted = seeds.event("TICKET", true, 5, "excellent", NOW.minusSeconds(3_600), NOW.plusSeconds(7_200),
				"OPEN", NOW.minusSeconds(60));
		// when
		// then
		for (UUID event : new UUID[] {notStarted, canceled, drawn}) {
			assertThatThrownBy(() -> entryService.enter(command(userId, event, UUID.randomUUID(),
					tickets(TicketGrade.BRONZE, 1))))
					.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_OPEN));
		}
		assertThatThrownBy(() -> entryService.enter(command(userId, deleted, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_FOUND));
		assertThatThrownBy(() -> entryService.enter(command(userId, UUID.randomUUID(), UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_FOUND));
		assertThat(spentTickets(userId)).isZero();
	}

	@Test
	@DisplayName("멤버십이 이벤트의 최소 등급보다 낮으면 거절하고 같거나 높으면 허용하며 관리자는 응모할 수 없다")
	void membershipRuleAndAdminAreChecked() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 3);
		UUID vipEvent = seeds.event("TICKET", true, 5, "vip", NOW.minusSeconds(3_600), NOW.plusSeconds(7_200),
				"OPEN", null);
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(new EntryCommand(userId, false, Membership.EXCELLENT, vipEvent,
				UUID.randomUUID(), tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.ENTRY_MEMBERSHIP_NOT_MET));
		assertThat(entryService.enter(new EntryCommand(userId, false, Membership.VIP, vipEvent, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1))).isCreated()).isTrue();
		assertThat(entryService.enter(new EntryCommand(userId, false, Membership.VVIP, vipEvent, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1))).isCreated()).isTrue();
		assertThatThrownBy(() -> entryService.enter(new EntryCommand(userId, true, null, vipEvent, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.ADMIN_ENTRY_FORBIDDEN));
		assertThat(usedTicketCount(userId, vipEvent)).isEqualTo(2);
	}

	@Test
	@DisplayName("음수 장수나 허용되지 않는 요청은 요청 형식 오류이고 응모권을 쓰지 않는 이벤트에 응모권을 담으면 거절한다")
	void invalidRequestShapesAreRejected() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 2);
		UUID weighted = weightedEvent(5);
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, weighted, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, -1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.INVALID_ENTRY_REQUEST));
		assertThatThrownBy(() -> entryService.enter(command(userId, weighted, UUID.randomUUID(), tickets())))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.INVALID_ENTRY_REQUEST));
		assertThatThrownBy(() -> entryService.enter(command(userId, weighted, UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 0))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.INVALID_ENTRY_REQUEST));
		assertThatThrownBy(() -> entryService.enter(command(userId, noTicketEvent(), UUID.randomUUID(),
				tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.INVALID_ENTRY_REQUEST));
		assertThat(spentTickets(userId)).isZero();
	}
}
