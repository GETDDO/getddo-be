package com.getddo.db.entry;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.service.MyEntryStatusService;
import com.getddo.core.ticket.domain.TicketGrade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 MySQL에서 이벤트 목록용 내 응모 이벤트 조회(R7)가 접수 완료된 응모만 응모한 것으로 보는지 검증한다. */
class MyEntryStatusIntegrationTest extends EntryIntegrationTestSupport {

	@Autowired
	private MyEntryStatusService service;

	@Test
	@DisplayName("접수된 이벤트만 돌려주고 거절만 있는 이벤트·응모가 없는 이벤트·다른 사용자의 응모는 제외한다")
	void returnsOnlyEventsWithAcceptedEntryOfTheUser() {
		// given
		UUID other = seeds.user();
		giveTickets(userId, TicketGrade.GOLD, 6);
		giveTickets(other, TicketGrade.GOLD, 2);
		UUID entered = weightedEvent(5);
		UUID rejectedOnly = weightedEvent(2);
		UUID otherOnly = weightedEvent(5);
		UUID untouched = weightedEvent(5);
		entryService.enter(command(userId, entered, UUID.randomUUID(), tickets(TicketGrade.GOLD, 1)));
		assertThatThrownBy(() -> entryService.enter(
				command(userId, rejectedOnly, UUID.randomUUID(), tickets(TicketGrade.GOLD, 5))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		entryService.enter(command(other, otherOnly, UUID.randomUUID(), tickets(TicketGrade.GOLD, 1)));
		List<UUID> pageEvents = List.of(entered, rejectedOnly, otherOnly, untouched);
		// when
		Set<UUID> mine = service.findEnteredEventIds(userId, pageEvents);
		Set<UUID> others = service.findEnteredEventIds(other, pageEvents);
		// then
		assertThat(mine).containsExactly(entered);
		assertThat(others).containsExactly(otherOnly);
	}

	@Test
	@DisplayName("같은 이벤트에 여러 번 응모해도 한 번만 담고, 목록에 없는 이벤트는 담지 않는다")
	void listsEachEnteredEventOnceAndOnlyFromGivenEvents() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 6);
		UUID entered = weightedEvent(5);
		UUID enteredOutsidePage = weightedEvent(5);
		entryService.enter(command(userId, entered, UUID.randomUUID(), tickets(TicketGrade.GOLD, 1)));
		entryService.enter(command(userId, entered, UUID.randomUUID(), tickets(TicketGrade.GOLD, 1)));
		entryService.enter(command(userId, enteredOutsidePage, UUID.randomUUID(), tickets(TicketGrade.GOLD, 1)));
		// when
		Set<UUID> result = service.findEnteredEventIds(userId, List.of(entered, UUID.randomUUID()));
		// then
		assertThat(result).containsExactly(entered);
	}

	@Test
	@DisplayName("응모 기록이 없는 사용자와 존재하지 않는 이벤트 ID는 빈 집합이다")
	void emptyForUnknownUserOrEvents() {
		assertThat(service.findEnteredEventIds(seeds.user(), List.of(weightedEvent(5)))).isEmpty();
		assertThat(service.findEnteredEventIds(userId, List.of(UUID.randomUUID()))).isEmpty();
	}
}
