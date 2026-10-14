package com.getddo.core.entry.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.entry.exception.EntryErrorCode;
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
		Entry entry = Entry.accepted(UUID.randomUUID(), participant,
				Map.of(TicketGrade.BRONZE, 1L, TicketGrade.GOLD, 2L), AT);
		// then
		assertThat(entry.getStatus()).isEqualTo(EntryStatus.ACCEPTED);
		assertThat(entry.getRequestedTicketCount()).isEqualTo(3);
		assertThat(entry.getDeductedTicketCount()).isEqualTo(3);
		assertThat(entry.getRequestedTicketsByGrade()).containsEntry(TicketGrade.BRONZE, 1L)
				.containsEntry(TicketGrade.SILVER, 0L).containsEntry(TicketGrade.GOLD, 2L);
		assertThat(entry.getDeductedTicketsByGrade()).isEqualTo(entry.getRequestedTicketsByGrade());
		assertThat(entry.getRejectionCode()).isNull();
		assertThat(entry.getEventId()).isEqualTo(eventId);
		assertThat(entry.getParticipantId()).isEqualTo(participant.getId());
		assertThat(entry.getUserId()).isEqualTo(userId);
	}

	@Test
	@DisplayName("응모 요청은 만든 뒤 원본 맵을 바꿔도 달라지지 않고 꺼낸 맵은 바꿀 수 없다")
	void commandCopiesTickets() {
		// given
		Map<TicketGrade, Long> tickets = new java.util.EnumMap<>(TicketGrade.class);
		tickets.put(TicketGrade.BRONZE, 1L);
		EntryCommand command = new EntryCommand(UUID.randomUUID(), false, null, UUID.randomUUID(), UUID.randomUUID(),
				tickets);
		// when
		tickets.put(TicketGrade.GOLD, 5L);
		// then
		assertThat(command.getTickets()).containsOnlyKeys(TicketGrade.BRONZE);
		assertThatThrownBy(() -> command.getTickets().put(TicketGrade.SILVER, 1L))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("응모는 음수 수량이나 요청보다 많은 차감을 거절한다")
	void entryRejectsInvalidQuantities() {
		// given
		UUID id = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> accepted(id, -1, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> accepted(id, 1, 2)).isInstanceOf(IllegalArgumentException.class);
		assertThat(accepted(id, 0, 0).getDeductedTicketCount()).isZero();
	}

	private static Entry accepted(UUID id, int requested, int deducted) {
		Map<TicketGrade, Long> byGrade = requested > 0 ? Map.of(TicketGrade.BRONZE, (long) requested) : Map.of();
		return new Entry(id, id, id, id, EntryStatus.ACCEPTED, requested, byGrade, deducted, null, null, AT);
	}

	@Test
	@DisplayName("거절된 응모는 응모자와 차감 없이 요청 장수와 거절 코드·사유를 남긴다")
	void rejectedEntryKeepsRequestWithoutDeduction() {
		// given
		UUID id = UUID.randomUUID();
		// when
		Entry entry = Entry.rejected(id, UUID.randomUUID(), UUID.randomUUID(), Map.of(TicketGrade.SILVER, 2L),
				EntryErrorCode.TICKET_LIMIT_EXCEEDED, AT);
		// then
		assertThat(entry.isRejected()).isTrue();
		assertThat(entry.getParticipantId()).isNull();
		assertThat(entry.getRequestedTicketCount()).isEqualTo(2);
		assertThat(entry.getRequestedTicketsByGrade()).containsEntry(TicketGrade.SILVER, 2L);
		assertThat(entry.getDeductedTicketCount()).isZero();
		assertThat(entry.getDeductedTicketsByGrade().values()).containsOnly(0L);
		assertThat(entry.getRejectionCode()).isEqualTo("ENTRY-007");
		assertThat(entry.getRejectionReason()).isEqualTo(EntryErrorCode.TICKET_LIMIT_EXCEEDED.getMessage());
	}

	@Test
	@DisplayName("등급별 요청 장수의 합이 요청 수량과 다르거나 음수·null이 있으면 만들 수 없다")
	void entryRejectsInconsistentGradeCounts() {
		// given
		UUID id = UUID.randomUUID();
		Map<TicketGrade, Long> nullValue = new java.util.EnumMap<>(TicketGrade.class);
		nullValue.put(TicketGrade.GOLD, null);
		// when
		// then
		assertThatThrownBy(() -> new Entry(id, id, id, id, EntryStatus.ACCEPTED, 3, Map.of(TicketGrade.GOLD, 2L), 3,
				null, null, AT)).isInstanceOf(IllegalArgumentException.class);
		assertThat(new Entry(id, id, id, id, EntryStatus.ACCEPTED, 0, Map.of(), 0, null, null, AT)
				.getRequestedTicketsByGrade().values()).containsOnly(0L);
		assertThatThrownBy(() -> new Entry(id, id, id, id, EntryStatus.ACCEPTED, 1,
				Map.of(TicketGrade.GOLD, 2L, TicketGrade.SILVER, -1L), 1, null, null, AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Entry(id, id, id, id, EntryStatus.ACCEPTED, 1, nullValue, 1, null, null, AT))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("상태와 맞지 않는 응모자·차감·거절 정보는 만들 수 없다")
	void entryRejectsInconsistentStatusFields() {
		// given
		UUID id = UUID.randomUUID();
		Map<TicketGrade, Long> one = Map.of(TicketGrade.GOLD, 1L);
		// when
		// then
		assertThatThrownBy(() -> new Entry(id, null, id, id, EntryStatus.ACCEPTED, 0, Map.of(), 0, null, null, AT))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new Entry(id, id, id, id, EntryStatus.ACCEPTED, 0, Map.of(), 0, "ENTRY-005", "사유", AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Entry(id, id, id, id, EntryStatus.REJECTED, 1, one, 0, "ENTRY-005", "사유", AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Entry(id, null, id, id, EntryStatus.REJECTED, 1, one, 1, "ENTRY-005", "사유", AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Entry(id, null, id, id, EntryStatus.REJECTED, 1, one, 0, null, "사유", AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Entry(id, null, id, id, EntryStatus.REJECTED, 1, one, 0, "ENTRY-005", " ", AT))
				.isInstanceOf(IllegalArgumentException.class);
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
