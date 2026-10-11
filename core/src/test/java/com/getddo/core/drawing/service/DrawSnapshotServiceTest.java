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

	/** 원본 저장소와 시각을 고정하며 제외 저장소 대역을 주입하지 않는다. */
	@BeforeEach
	void setUp() {
		repository = mock(DrawSnapshotRepository.class);
		service = new DrawSnapshotService(repository, new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(event(EventType.TICKET, true, NOW.minusSeconds(300))));
		when(repository.findInitial(eventId)).thenReturn(Optional.empty());
		when(repository.saveInitial(any())).thenAnswer(call -> call.getArgument(0));
	}

	/** 추가 응모의 등급별 장수·가중치와 원본 근거가 함께 보존되는지 검증한다. */
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

	/** 누적 합계가 같아도 개별 응모 사용 근거의 불일치는 저장 전에 거절한다. */
	@Test
	void detectsPerEntryMismatchEvenWhenTotalsMatch() {
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(4,
				entry(1, TicketGrade.GOLD, TicketGrade.GOLD), entry(3, TicketGrade.BRONZE, TicketGrade.BRONZE))));
		assertError(INVALID_EVIDENCE);
		verify(repository, never()).saveInitial(any());
	}

	/** 추가 응모 사이에 중복 사용된 티켓을 후보 확정 전에 거절한다. */
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

	/** 누적 사용 장수 불일치와 응모권 사용 이벤트의 0장 사용을 거절한다. */
	@Test
	void rejectsAccumulatedMismatchAndZeroTicketUse() {
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(2, entry(1, TicketGrade.GOLD))));
		assertError(INVALID_EVIDENCE);
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(0, entry(0))));
		assertError(INVALID_EVIDENCE);
	}

	/** 응모권 미사용·가중치 미적용 후보의 가중치는 1로 보존한다. */
	@Test
	void noTicketAndUnweightedUseHaveWeightOne() {
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(event(EventType.NO_TICKET, false, NOW.minusSeconds(300))));
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(0, entry(0))));
		assertThat(service.prepareInitial(eventId).candidates().getFirst().weight()).isEqualTo(1);
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(event(EventType.TICKET, false, NOW.minusSeconds(300))));
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(1, entry(1, TicketGrade.GOLD))));
		assertThat(service.prepareInitial(eventId).candidates().getFirst().weight()).isEqualTo(1);
	}

	/** 응모자 없음은 후보 없이 정상 종료하고 어뷰징 제외 종료를 새로 만들지 않는다. */
	@Test
	void emptyEventTerminatesWithoutCandidates() {
		when(repository.findParticipants(eventId)).thenReturn(List.of());
		DrawSnapshot snapshot = service.prepareInitial(eventId);
		assertThat(snapshot.status()).isEqualTo(DrawRunStatus.NO_ENTRIES);
		assertThat(snapshot.candidates()).isEmpty();
	}

	/** 제외 저장소 없이 서로 다른 사용자의 모든 정상 응모를 READY 후보로 확정한다. */
	@Test
	void preparesAllValidParticipantsWithoutExclusionProvider() {
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(event(EventType.NO_TICKET, false, NOW.minusSeconds(300))));
		Participant first = participant(0, entry(0));
		Participant second = new Participant(UUID.randomUUID(), UUID.randomUUID(), 0, "VIP", "USER", List.of(entry(0)));
		when(repository.findParticipants(eventId)).thenReturn(List.of(first, second));

		DrawSnapshot snapshot = service.prepareInitial(eventId);

		assertThat(snapshot.status()).isEqualTo(DrawRunStatus.READY);
		assertThat(snapshot.candidates()).extracting(DrawSnapshot.Candidate::participantId)
				.containsExactly(first.id(), second.id());
		assertThat(snapshot.candidates()).allSatisfy(candidate -> {
			assertThat(candidate.ticketCount()).isZero();
			assertThat(candidate.weight()).isEqualTo(1);
			assertThat(candidate.eligibilityEvidence().excluded()).isFalse();
		});
	}

	/** 과거 NO_CANDIDATES를 포함한 고정 실행은 원본 재조회나 재계산 없이 반환한다. */
	@ParameterizedTest
	@EnumSource(value = DrawRunStatus.class, names = {"READY", "RUNNING", "CONFIRMED", "NO_ENTRIES", "NO_CANDIDATES"})
	void replaysFixedRunWithoutReadingOriginals(DrawRunStatus status) {
		DrawSnapshot saved = new DrawSnapshot(UUID.randomUUID(), eventId, status, NOW, "v1", null, List.of());
		when(repository.findInitial(eventId)).thenReturn(Optional.of(saved));
		assertThat(service.prepareInitial(eventId)).isSameAs(saved);
		verify(repository, never()).findParticipants(any());
		verify(repository, never()).saveInitial(any());
	}

	/** 기존 5분 경계와 취소·삭제 이벤트의 실행 거절을 유지한다. */
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

	/** 티켓 소유자 불일치와 마감 이후 응모를 원본 정합성 오류로 거절한다. */
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

	/** 단일 응모 이벤트에 추가 응모가 기록됐으면 전체 준비를 거절한다. */
	@Test
	void rejectsAdditionalEntriesForSingleEntryEvents() {
		when(repository.lockEvent(eventId)).thenReturn(Optional.of(event(EventType.NO_TICKET, false, NOW.minusSeconds(300))));
		when(repository.findParticipants(eventId)).thenReturn(List.of(participant(0, entry(0), entry(0))));
		assertError(INVALID_EVIDENCE);
		verify(repository, never()).saveInitial(any());
	}

	/** 지정한 응모권·가중치 조건의 이벤트 입력을 구성한다. */
	private Event event(EventType type, boolean weighted, Instant endsAt) {
		return new Event(eventId, type, weighted, endsAt, EventStatus.CLOSED, false, "vip",
				List.of(new Prize(UUID.randomUUID(), 1, 2)));
	}
	/** 같은 사용자의 누적 수량과 원본 응모를 구성한다. */
	private Participant participant(long count, Entry... entries) {
		return new Participant(participantId, userId, count, "VIP", "USER", List.of(entries));
	}
	/** 등급별로 서로 다른 티켓 사용 근거를 구성한다. */
	private Entry entry(long count, TicketGrade... grades) {
		return new Entry(UUID.randomUUID(), count, NOW.minusSeconds(301), java.util.Arrays.stream(grades)
				.map(grade -> new Use(UUID.randomUUID(), UUID.randomUUID(), userId, grade)).toList());
	}
	/** 실제 준비 호출이 지정한 도메인 오류로 거절되는지 확인한다. */
	private void assertError(com.getddo.core.drawing.exception.DrawingErrorCode code) {
		assertThatThrownBy(() -> service.prepareInitial(eventId)).isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(code);
	}
}
