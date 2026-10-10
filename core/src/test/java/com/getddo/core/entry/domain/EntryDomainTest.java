package com.getddo.core.entry.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.ticket.domain.TicketGrade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntryDomainTest {

	private static final Instant AT = Instant.parse("2026-09-15T03:00:00Z");

	@Test
	@DisplayName("접수된 응모는 요청한 만큼 모두 차감한 상태로 만들어지고 이벤트는 응모자에서 가져온다")
	void acceptedEntryDeductsEverythingRequested() {
		// given
		UUID eventId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		EntryParticipant participant = new EntryParticipant(UUID.randomUUID(), eventId, userId, 0, AT);
		// when
		Entry entry = Entry.accepted(UUID.randomUUID(), participant, userId, 3, AT);
		// then
		assertThat(entry.getRequestedTicketCount()).isEqualTo(3);
		assertThat(entry.getDeductedTicketCount()).isEqualTo(3);
		assertThat(entry.getEventId()).isEqualTo(eventId);
		assertThat(entry.getParticipantId()).isEqualTo(participant.getId());
	}

	@Test
	@DisplayName("응모는 음수 수량이나 요청보다 많은 차감을 거절한다")
	void entryRejectsInvalidQuantities() {
		// given
		UUID id = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> new Entry(id, id, id, id, -1, 0, AT)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Entry(id, id, id, id, 1, 2, AT)).isInstanceOf(IllegalArgumentException.class);
		assertThat(new Entry(id, id, id, id, 0, 0, AT).getDeductedTicketCount()).isZero();
	}

	@Test
	@DisplayName("처음 응모하는 사용자의 응모자는 ID 없이 누적 차감 0으로 만들어지고 음수 누적은 거절한다")
	void firstParticipantStartsAtZero() {
		// given
		UUID eventId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		// when
		EntryParticipant participant = EntryParticipant.first(eventId, userId, AT);
		// then
		assertThat(participant.getId()).isNull();
		assertThat(participant.getUsedTicketCount()).isZero();
		assertThatThrownBy(() -> new EntryParticipant(null, eventId, userId, -1, AT))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("영수증은 모든 등급의 키를 가지며 합계를 계산하고 반환된 맵은 수정할 수 없다")
	void receiptHasAllGradesAndTotal() {
		// given
		EntryReceipt receipt = new EntryReceipt(UUID.randomUUID(), UUID.randomUUID(), "이벤트",
				Map.of(TicketGrade.GOLD, 2L, TicketGrade.SILVER, 1L), AT, true);
		// when
		// then
		assertThat(receipt.getTicketsByGrade()).containsEntry(TicketGrade.BRONZE, 0L)
				.containsEntry(TicketGrade.SILVER, 1L).containsEntry(TicketGrade.GOLD, 2L);
		assertThat(receipt.getTicketCount()).isEqualTo(3);
		assertThatThrownBy(() -> receipt.getTicketsByGrade().put(TicketGrade.BRONZE, 9L))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("자격 조회는 사유가 없을 때만 응모할 수 있다고 본다")
	void eligibilityCanEnterOnlyWithoutReasons() {
		// given
		UUID eventId = UUID.randomUUID();
		// when
		// then
		assertThat(new EntryEligibility(eventId, List.of(), 0, 5L, 3, AT).isCanEnter()).isTrue();
		assertThat(new EntryEligibility(eventId, List.of("EVENT_NOT_OPEN"), 0, 5L, 3, AT).isCanEnter()).isFalse();
	}
}
