package com.getddo.db.entry;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.entry.domain.EntryCommand;
import com.getddo.core.entry.domain.EntryStatistics;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.service.EntryStatisticsService;
import com.getddo.core.ticket.domain.RefundCommand;
import com.getddo.core.ticket.domain.TicketGrade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 MySQL에서 응모 현황(E03)이 접수 완료된 응모자 기준으로 합계를 맞게 집계하는지 검증한다. */
class EntryStatisticsIntegrationTest extends EntryIntegrationTestSupport {

	@Autowired
	private EntryStatisticsService statisticsService;

	@Test
	@DisplayName("응모자는 중복 없이 세고 차감은 추가 응모까지 합치며 본인 수량은 본인 것만 보여 준다")
	void countsDistinctParticipantsAndSumsSpentTickets() {
		// given
		UUID other = seeds.user();
		giveTickets(userId, TicketGrade.GOLD, 5);
		giveTickets(other, TicketGrade.SILVER, 4);
		UUID event = weightedEvent(5);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.GOLD, 2)));
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.GOLD, 1)));
		entryService.enter(command(other, event, UUID.randomUUID(), tickets(TicketGrade.SILVER, 4)));
		// when
		EntryStatistics mine = statisticsService.getStatistics(userId, event);
		EntryStatistics theirs = statisticsService.getStatistics(other, event);
		EntryStatistics stranger = statisticsService.getStatistics(seeds.user(), event);
		// then
		assertThat(mine.getParticipantCount()).isEqualTo(2);
		assertThat(mine.getTotalSpentTicketCount()).isEqualTo(7);
		assertThat(mine.getMySpentTicketCount()).isEqualTo(3);
		assertThat(theirs.getMySpentTicketCount()).isEqualTo(4);
		assertThat(stranger.getParticipantCount()).isEqualTo(2);
		assertThat(stranger.getTotalSpentTicketCount()).isEqualTo(7);
		assertThat(stranger.getMySpentTicketCount()).isZero();
		assertThat(mine.getServerTime()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("응모가 없는 이벤트는 모두 0이고 다른 이벤트의 응모는 섞이지 않는다")
	void emptyEventIsZeroAndEventsAreIndependent() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 2);
		UUID busy = weightedEvent(5);
		UUID quiet = weightedEvent(5);
		entryService.enter(command(userId, busy, UUID.randomUUID(), tickets(TicketGrade.GOLD, 2)));
		// when
		EntryStatistics empty = statisticsService.getStatistics(userId, quiet);
		// then
		assertThat(empty.getParticipantCount()).isZero();
		assertThat(empty.getTotalSpentTicketCount()).isZero();
		assertThat(empty.getMySpentTicketCount()).isZero();
	}

	@Test
	@DisplayName("거절 이력은 응모자와 차감에 들어가지 않고, 응모권을 반환해도 차감 합계에서 빼지 않는다")
	void rejectionsAreExcludedAndRefundsAreNotSubtracted() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 6);
		UUID event = weightedEvent(5);
		UUID accepted = UUID.randomUUID();
		entryService.enter(command(userId, event, accepted, tickets(TicketGrade.GOLD, 2)));
		assertThatThrownBy(() -> entryService.enter(command(userId, event, UUID.randomUUID(),
				tickets(TicketGrade.GOLD, 4)))).satisfies(e -> assertEntryError(e, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		UUID admin = seeds.user();
		assertThatThrownBy(() -> entryService.enter(new EntryCommand(admin, true, null, event, UUID.randomUUID(),
				tickets(TicketGrade.GOLD, 1)))).satisfies(e -> assertEntryError(e, EntryErrorCode.ADMIN_ENTRY_FORBIDDEN));
		// when
		EntryStatistics before = statisticsService.getStatistics(userId, event);
		transaction.execute(status -> ticketRefundService.refund(new RefundCommand(accepted, "이벤트 취소")));
		EntryStatistics after = statisticsService.getStatistics(userId, event);
		// then
		assertThat(before.getParticipantCount()).isEqualTo(1);
		assertThat(before.getTotalSpentTicketCount()).isEqualTo(2);
		assertThat(after.getParticipantCount()).isEqualTo(1);
		assertThat(after.getTotalSpentTicketCount()).isEqualTo(2);
		assertThat(after.getMySpentTicketCount()).isEqualTo(2);
		assertThat(statisticsService.getStatistics(admin, event).getMySpentTicketCount()).isZero();
	}

	@Test
	@DisplayName("없는 이벤트와 삭제된 이벤트는 ENTRY-004이다")
	void missingAndDeletedEventsAreNotFound() {
		// given
		UUID deleted = seeds.event("TICKET", true, 5, "excellent", NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
				"OPEN", NOW.minusSeconds(60));
		// when
		// then
		assertThatThrownBy(() -> statisticsService.getStatistics(userId, UUID.randomUUID()))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_FOUND));
		assertThatThrownBy(() -> statisticsService.getStatistics(userId, deleted))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_FOUND));
	}
}
