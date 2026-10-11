package com.getddo.core.drawing.service;

import java.math.BigInteger;
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
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.DrawResultRepository;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DrawConfirmationServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
	private final UUID runId = UUID.randomUUID(), eventId = UUID.randomUUID(), prizeId = UUID.randomUUID();
	private final DrawResultRepository repository = mock(DrawResultRepository.class);
	private final DrawSnapshotRepository snapshots = mock(DrawSnapshotRepository.class);
	private final DrawRandom random = mock(DrawRandom.class);
	private final DrawingSelectionService selection = spy(new DrawingSelectionService(random));
	private final DrawConfirmationService service = new DrawConfirmationService(repository, snapshots, selection,
			new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	private DrawSnapshot snapshot;

	@BeforeEach
	void setUp() {
		snapshot = snapshot(DrawRunStatus.READY, List.of(candidate(UUID.randomUUID(), 1)),
				List.of(new DrawSnapshotSource.Prize(prizeId, 1, 2)));
		when(repository.findEventId(runId)).thenReturn(Optional.of(eventId));
		when(snapshots.lockEvent(eventId)).thenReturn(Optional.of(new DrawSnapshotSource.Event(eventId,
				EventType.NO_TICKET, false, NOW.minusSeconds(300), EventStatus.CLOSED, false, "vip", List.of())));
		when(repository.lockSnapshot(runId)).thenReturn(Optional.of(snapshot));
		when(repository.findCurrentAwards(eventId)).thenReturn(List.of());
		when(random.nextBelow(any())).thenReturn(BigInteger.ZERO);
	}

	@Test
	@DisplayName("상위 등수부터 선정하고 남은 자리와 결과를 한 번 저장한다")
	void storesSelectionAndUnfilledInRankOrder() {
		// given
		UUID secondPrize = UUID.randomUUID();
		snapshot = snapshot(DrawRunStatus.READY, List.of(candidate(UUID.randomUUID(), 1)),
				List.of(new DrawSnapshotSource.Prize(secondPrize, 2, 1), new DrawSnapshotSource.Prize(prizeId, 1, 1)));
		when(repository.lockSnapshot(runId)).thenReturn(Optional.of(snapshot));
		when(repository.saveConfirmed(eq(snapshot), anyList(), eq(NOW))).thenAnswer(invocation -> {
			List<DrawSelection> results = invocation.getArgument(1);
			return new DrawConfirmation(runId, eventId, NOW,
					results.stream().map(result -> new DrawResult(UUID.randomUUID(), result, NOW)).toList());
		});
		// when
		DrawConfirmation confirmed = service.confirm(runId);
		// then
		assertThat(confirmed.getResults()).hasSize(2);
		assertThat(confirmed.getResults().getFirst().getSelection().getPrizeId()).isEqualTo(prizeId);
		assertThat(confirmed.getResults().getFirst().getSelection().getSelectionOrder()).isEqualTo(1);
		assertThat(confirmed.getResults().getLast().getSelection().getResultType()).isEqualTo(DrawSelectionResultType.UNFILLED);
		verify(repository).saveConfirmed(eq(snapshot), anyList(), eq(NOW));
		var ordered = inOrder(repository, snapshots);
		ordered.verify(repository).findEventId(runId);
		ordered.verify(snapshots).lockEvent(eventId);
		ordered.verify(repository).lockSnapshot(runId);
	}

	@Test
	@DisplayName("확정된 실행은 선정기·현재 배정 조회 없이 저장된 ID와 순서를 반환한다")
	void confirmedRequestDoesNotSelectAgain() {
		// given
		snapshot = snapshot(DrawRunStatus.CONFIRMED, snapshot.candidates(), snapshot.rules().prizes());
		when(repository.lockSnapshot(runId)).thenReturn(Optional.of(snapshot));
		List<DrawSelection> selections = new DrawingSelectionService(bound -> BigInteger.ZERO).select(
				List.of(new DrawCandidate(snapshot.candidates().getFirst().id(), snapshot.candidates().getFirst().userId(), 1)),
				List.of(new DrawPrize(prizeId, 1, 2)), false);
		DrawConfirmation stored = new DrawConfirmation(runId, eventId, NOW,
				selections.stream().map(result -> new DrawResult(UUID.randomUUID(), result, NOW)).toList());
		when(repository.findConfirmed(snapshot)).thenReturn(stored);
		// when / then
		assertThat(service.confirm(runId)).isSameAs(stored);
		verifyNoInteractions(selection, random);
		verify(repository, never()).findCurrentAwards(any());
		verify(repository, never()).saveConfirmed(any(), anyList(), any());
	}

	@ParameterizedTest
	@EnumSource(value = DrawRunStatus.class, names = {"PREPARING", "RUNNING", "FAILED", "NO_ENTRIES", "NO_CANDIDATES"})
	@DisplayName("READY·CONFIRMED 이외 실행을 재선정하지 않는다")
	void rejectsUnreadyStates(DrawRunStatus status) {
		// given
		when(repository.lockSnapshot(runId)).thenReturn(Optional.of(snapshot(status, snapshot.candidates(), snapshot.rules().prizes())));
		// when / then
		assertInvalid();
		verifyNoInteractions(selection);
	}

	@Test
	@DisplayName("같은 사용자의 서로 다른 후보 ID를 거절한다")
	void rejectsDuplicateUserBeforeSelection() {
		// given
		UUID user = UUID.randomUUID();
		when(repository.lockSnapshot(runId)).thenReturn(Optional.of(snapshot(DrawRunStatus.READY,
				List.of(candidate(user, 1), candidate(user, 1)), snapshot.rules().prizes())));
		// when / then
		assertInvalid();
		verifyNoInteractions(selection);
	}

	@Test
	@DisplayName("기존 유효 당첨의 대상 경품·사용자 충돌을 거절한다")
	void rejectsCurrentAwardConflict() {
		// given
		when(repository.findCurrentAwards(eventId)).thenReturn(List.of(new DrawSelection(prizeId, 1, 1, 1,
				UUID.randomUUID(), UUID.randomUUID(), DrawSelectionResultType.SELECTED)));
		// when / then
		assertInvalid();
		verifyNoInteractions(selection);
	}

	@Test
	@DisplayName("실행에 없는 후보를 선정기가 반환해도 저장하지 않는다")
	void rejectsForeignCandidateReturnedBySelection() {
		// given
		doReturn(List.of(new DrawSelection(prizeId, 1, 1, 1, UUID.randomUUID(), UUID.randomUUID(), DrawSelectionResultType.SELECTED),
				DrawSelection.unfilled(new DrawPrize(prizeId, 1, 2), 2))).when(selection).select(anyList(), anyList(), anyBoolean());
		// when / then
		assertInvalid();
		verify(repository, never()).saveConfirmed(any(), anyList(), any());
	}

	@Test
	@DisplayName("확정 전 실패 후 같은 저장 스냅샷으로 다시 선정할 수 있다")
	void retriesUsingSameSnapshotAfterFailure() {
		// given
		when(repository.saveConfirmed(eq(snapshot), anyList(), any())).thenThrow(new IllegalStateException("저장 실패"))
				.thenAnswer(invocation -> {
			List<DrawSelection> results = invocation.getArgument(1);
			return new DrawConfirmation(runId, eventId, NOW,
					results.stream().map(result -> new DrawResult(UUID.randomUUID(), result, NOW)).toList());
		});
		// when / then
		assertThatThrownBy(() -> service.confirm(runId)).isInstanceOf(IllegalStateException.class);
		assertThat(service.confirm(runId).getRunId()).isEqualTo(runId);
		verify(selection, times(2)).select(anyList(), anyList(), eq(false));
		verify(snapshots, never()).findParticipants(any());
	}

	@Test
	@DisplayName("다른 경품의 기존 당첨 사용자도 후보에 있으면 거절한다")
	void rejectsCurrentWinnerInCandidates() {
		// given
		when(repository.findCurrentAwards(eventId)).thenReturn(List.of(new DrawSelection(UUID.randomUUID(), 2, 1, 1,
				UUID.randomUUID(), snapshot.candidates().getFirst().userId(), DrawSelectionResultType.SELECTED)));
		// when / then
		assertInvalid();
		verifyNoInteractions(selection);
	}

	@Test
	@DisplayName("READY에 일부 결과가 남아 있으면 기존 결과를 덮어쓰지 않는다")
	void rejectsPartialResults() {
		// given
		when(repository.hasResults(runId)).thenReturn(true);
		// when / then
		assertInvalid();
		verifyNoInteractions(selection);
	}

	@Test
	@DisplayName("알 수 없는 알고리즘 버전을 현재 선정기로 재해석하지 않는다")
	void rejectsUnknownAlgorithm() {
		// given
		when(repository.lockSnapshot(runId)).thenReturn(Optional.of(new DrawSnapshot(runId, eventId,
				DrawRunStatus.READY, NOW, "unknown", snapshot.rules(), snapshot.candidates())));
		// when / then
		assertInvalid();
		verifyNoInteractions(selection);
	}

	@Test
	@DisplayName("취소된 이벤트의 READY 실행은 확정하지 않는다")
	void refusesCanceledEvent() {
		// given
		when(snapshots.lockEvent(eventId)).thenReturn(Optional.of(new DrawSnapshotSource.Event(eventId,
				EventType.NO_TICKET, false, NOW.minusSeconds(300), EventStatus.CANCELED, false, "vip", List.of())));
		// when / then
		assertThatThrownBy(() -> service.confirm(runId)).isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(NOT_EXECUTABLE);
		verifyNoInteractions(selection);
	}

	@Test
	@DisplayName("결과의 중복 당첨·순서 누락·조기 미충원·잘못된 자리를 저장 전에 거절한다")
	void rejectsInvalidSelectionShape() {
		// given
		var first = candidate(UUID.randomUUID(), 1);
		var second = candidate(UUID.randomUUID(), 1);
		DrawSnapshot input = snapshot(DrawRunStatus.READY, List.of(first, second), snapshot.rules().prizes());
		DrawPrize prize = new DrawPrize(prizeId, 1, 2);
		var selected = DrawSelection.selected(prize, 1, 1, new DrawCandidate(first.id(), first.userId(), 1));
		List<List<DrawSelection>> invalidResults = List.of(
				List.of(selected, DrawSelection.selected(prize, 2, 2, new DrawCandidate(first.id(), first.userId(), 1))),
				List.of(selected, DrawSelection.selected(prize, 2, 3, new DrawCandidate(second.id(), second.userId(), 1))),
				List.of(selected, DrawSelection.unfilled(prize, 2)),
				List.of(selected, DrawSelection.selected(prize, 3, 2, new DrawCandidate(second.id(), second.userId(), 1))));
		// when / then
		for (var results : invalidResults) {
			assertThatThrownBy(() -> DrawConfirmationService.validateResults(input, results))
					.isInstanceOf(BusinessException.class)
					.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(INVALID_RUN);
		}
	}

	private void assertInvalid() {
		assertThatThrownBy(() -> service.confirm(runId)).isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(INVALID_RUN);
	}
	private DrawSnapshot snapshot(DrawRunStatus status, List<DrawSnapshot.Candidate> candidates, List<DrawSnapshotSource.Prize> prizes) {
		return new DrawSnapshot(runId, eventId, status, NOW.minusSeconds(1), DrawSnapshotService.ALGORITHM_VERSION,
				new DrawSnapshot.Rules(1, false, "NO_TICKET", "vip", 1, 3, 5, prizes), candidates);
	}
	private DrawSnapshot.Candidate candidate(UUID user, long weight) {
		return new DrawSnapshot.Candidate(UUID.randomUUID(), UUID.randomUUID(), user, 0, weight,
				new DrawSnapshot.EntryEvidence(1, 0, 0, 0, List.of()),
				new DrawSnapshot.EligibilityEvidence(1, user, "VIP", "USER", false, NOW.minusSeconds(1)));
	}
}
