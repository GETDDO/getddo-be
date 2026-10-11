package com.getddo.core.drawing.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawSnapshotSource.*;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.ticket.domain.TicketGrade;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DrawSnapshotServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");
	private final UUID eventId = UUID.randomUUID(), participantId = UUID.randomUUID(), userId = UUID.randomUUID();
	private DrawSnapshotRepository repository;
	private DrawSnapshotService service;

	@BeforeEach
	void setUp() {
		repository = mock(DrawSnapshotRepository.class);
		service = new DrawSnapshotService(repository, new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(event(EventType.TICKET, true, NOW.minusSeconds(300))));
		when(repository.findInitial(eventId)).thenReturn(Optional.empty());
		when(repository.saveInitial(any())).thenAnswer(call -> call.getArgument(0));
	}

	@Test
	void sumsGradesAcrossAdditionalEntriesAndPreservesEvidence() {
		Entry first = entry(2, TicketGrade.GOLD, TicketGrade.SILVER);
		Entry additional = entry(1, TicketGrade.BRONZE);
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(3, first, additional)));
		DrawSnapshot snapshot = service.prepareInitial(eventId);
		assertThat(snapshot.status()).isEqualTo(DrawRunStatus.READY);
		DrawSnapshot.Candidate candidate = snapshot.candidates().getFirst();
		assertThat(candidate.ticketCount()).isEqualTo(3);
		assertThat(candidate.weight()).isEqualTo(9);
		assertThat(candidate.entryEvidence().entries()).containsExactly(first, additional);
		assertThat(candidate.entryEvidence().goldCount()).isEqualTo(1);
		assertThat(candidate.entryEvidence().silverCount()).isEqualTo(1);
		assertThat(candidate.entryEvidence().bronzeCount()).isEqualTo(1);
	}

	@Test
	void detectsPerEntryMismatchEvenWhenTotalsMatch() {
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(4,
				entry(1, TicketGrade.GOLD, TicketGrade.GOLD), entry(3, TicketGrade.BRONZE, TicketGrade.BRONZE))));
		assertError(INVALID_EVIDENCE);
		verify(repository, never()).saveInitial(any());
	}

	@Test
	@DisplayName("추가 응모에 같은 티켓이 중복되면 입력 확정을 거절한다")
	void rejectsSameTicketAcrossAdditionalEntries() {
		// given
		UUID ticketId = UUID.randomUUID();
		Entry first = new Entry(UUID.randomUUID(), 1, NOW.minusSeconds(301),
				List.of(new Use(UUID.randomUUID(), ticketId, userId, TicketGrade.GOLD)));
		Entry additional = new Entry(UUID.randomUUID(), 1, NOW.minusSeconds(301),
				List.of(new Use(UUID.randomUUID(), ticketId, userId, TicketGrade.GOLD)));
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(2, first, additional)));

		// when / then
		assertError(INVALID_EVIDENCE);
		verify(repository, never()).saveInitial(any());
	}

	@Test
	void rejectsAccumulatedMismatchAndZeroTicketUse() {
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(2, entry(1, TicketGrade.GOLD))));
		assertError(INVALID_EVIDENCE);
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(0, entry(0))));
		assertError(INVALID_EVIDENCE);
	}

	@Test
	void noTicketAndUnweightedUseHaveWeightOne() {
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(event(EventType.NO_TICKET, false, NOW.minusSeconds(300))));
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(0, entry(0))));
		assertThat(service.prepareInitial(eventId).candidates().getFirst().weight()).isEqualTo(1);
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(event(EventType.TICKET, false, NOW.minusSeconds(300))));
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(1, entry(1, TicketGrade.GOLD))));
		assertThat(service.prepareInitial(eventId).candidates().getFirst().weight()).isEqualTo(1);
	}

	@Test
	void terminatesWithoutCandidatesOnlyWhenThereAreNoEntries() {
		when(repository.findParticipants(eventId)).thenReturn(List.of());
		DrawSnapshot snapshot = service.prepareInitial(eventId);
		assertThat(snapshot.status()).isEqualTo(DrawRunStatus.NO_ENTRIES);
		assertThat(snapshot.candidates()).isEmpty();
	}

	@Test
	void includesEveryValidParticipantWithoutAnExclusionProvider() {
		Participant first = participant(1, entry(1, TicketGrade.GOLD));
		UUID otherUser = UUID.randomUUID();
		Entry otherEntry = new Entry(UUID.randomUUID(), 1, NOW.minusSeconds(301),
				List.of(new Use(UUID.randomUUID(), UUID.randomUUID(), otherUser, TicketGrade.SILVER)));
		Participant second = new Participant(UUID.randomUUID(), otherUser, 1, "VIP", "USER", List.of(otherEntry));
		when(repository.findParticipants(eventId)).thenReturn(List.of(first, second));

		DrawSnapshot snapshot = service.prepareInitial(eventId);

		assertThat(snapshot.status()).isEqualTo(DrawRunStatus.READY);
		assertThat(snapshot.candidates()).extracting(DrawSnapshot.Candidate::participantId)
				.containsExactly(first.id(), second.id());
		assertThat(snapshot.candidates()).extracting(DrawSnapshot.Candidate::weight).containsExactly(5L, 3L);
		assertThat(snapshot.candidates()).allSatisfy(candidate -> {
			assertThat(candidate.ticketCount()).isEqualTo(1);
			assertThat(candidate.eligibilityEvidence().excluded()).isFalse();
		});
	}

	@ParameterizedTest
	@EnumSource(value = DrawRunStatus.class, names = {"READY", "RUNNING", "CONFIRMED", "NO_ENTRIES", "NO_CANDIDATES"})
	void replaysFixedRunWithoutReadingOriginals(DrawRunStatus status) {
		DrawSnapshot saved = new DrawSnapshot(UUID.randomUUID(), eventId, status, NOW, "v1", null, List.of());
		when(repository.findInitial(eventId)).thenReturn(Optional.of(saved));
		assertThat(service.prepareInitial(eventId)).isSameAs(saved);
		verify(repository, never()).findParticipants(any());
		verify(repository, never()).saveInitial(any());
	}

	@Test
	void waitsUntilFiveMinuteBoundaryAndRejectsCanceledDeletedEvents() {
		Event early = event(EventType.TICKET, true, NOW.minusSeconds(299));
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(early));
		assertError(NOT_EXECUTABLE);
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(new Event(eventId, early.type(), true,
				NOW.minusSeconds(300), EventStatus.CANCELED, false, "vip", early.prizes())));
		assertError(NOT_EXECUTABLE);
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(new Event(eventId, early.type(), true,
				NOW.minusSeconds(300), EventStatus.CLOSED, true, "vip", early.prizes())));
		assertError(NOT_EXECUTABLE);
	}

	@Test
	void rejectsWrongTicketOwnerAndEntryAfterDeadline() {
		Use use = new Use(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), TicketGrade.GOLD);
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(1,
				new Entry(UUID.randomUUID(), 1, NOW.minusSeconds(301), List.of(use)))));
		assertError(INVALID_EVIDENCE);
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(1,
				new Entry(UUID.randomUUID(), 1, NOW, entry(1, TicketGrade.GOLD).uses()))));
		assertError(INVALID_EVIDENCE);
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(1,
				new Entry(UUID.randomUUID(), 1, NOW.minusSeconds(300), entry(1, TicketGrade.GOLD).uses()))));
		assertError(INVALID_EVIDENCE);
	}

	@Test
	void rejectsAdditionalEntriesForSingleEntryEvents() {
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(event(EventType.NO_TICKET, false, NOW.minusSeconds(300))));
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(0, entry(0), entry(0))));
		assertError(INVALID_EVIDENCE);
	}

	private Event event(EventType type, boolean weighted, Instant endsAt) {
		return new Event(eventId, type, weighted, endsAt, EventStatus.CLOSED, false, "vip",
				List.of(new Prize(UUID.randomUUID(), 1, 2)));
	}
	private Participant participant(long count, Entry... entries) {
		return new Participant(participantId, userId, count, "VIP", "USER", List.of(entries));
	}
	private Entry entry(long count, TicketGrade... grades) {
		return new Entry(UUID.randomUUID(), count, NOW.minusSeconds(301), java.util.Arrays.stream(grades)
				.map(grade -> new Use(UUID.randomUUID(), UUID.randomUUID(), userId, grade)).toList());
	}
	private void assertError(com.getddo.core.drawing.exception.DrawingErrorCode code) {
		assertThatThrownBy(() -> service.prepareInitial(eventId)).isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(code);
	}
}
