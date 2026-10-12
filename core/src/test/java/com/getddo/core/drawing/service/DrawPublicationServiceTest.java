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
import org.mockito.InOrder;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.*;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 저장소 대역을 이용한 공개 업무 규칙 검증. 실제 재추첨 연동 검증은 아니다. */
class DrawPublicationServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
	private final UUID eventId = UUID.randomUUID(), runId = UUID.randomUUID(), prizeId = UUID.randomUUID();
	private final UUID candidateId = UUID.randomUUID(), userId = UUID.randomUUID(), resultId = UUID.randomUUID();
	private final DrawPublicationRepository publications = mock(DrawPublicationRepository.class);
	private final DrawSnapshotRepository snapshots = mock(DrawSnapshotRepository.class);
	private final DrawResultRepository results = mock(DrawResultRepository.class);
	private final DrawPublicationService service = new DrawPublicationService(publications, snapshots, results,
			new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	private DrawSnapshot snapshot;
	private DrawResult selected;
	private DrawResult unfilled;

	@BeforeEach
	void setUp() {
		DrawSnapshot.Rules rules = new DrawSnapshot.Rules(1, false, "NO_TICKET", "NORMAL", 1, 3, 5,
				List.of(new DrawSnapshotSource.Prize(prizeId, 1, 2)));
		DrawSnapshot.Candidate candidate = new DrawSnapshot.Candidate(candidateId, UUID.randomUUID(), userId,
				0, 1, null, null);
		snapshot = new DrawSnapshot(runId, eventId, DrawRunStatus.CONFIRMED, NOW.minusSeconds(1),
				"test", rules, List.of(candidate));
		selected = new DrawResult(resultId, new DrawSelection(prizeId, 1, 1, 1,
				candidateId, userId, DrawSelectionResultType.SELECTED), NOW);
		unfilled = new DrawResult(UUID.randomUUID(), new DrawSelection(prizeId, 1, 2, null,
				null, null, DrawSelectionResultType.UNFILLED), NOW);
		when(snapshots.lockEvent(eventId)).thenReturn(Optional.of(event(NOW.minusSeconds(300), EventStatus.CLOSED)));
		when(snapshots.findInitial(eventId)).thenReturn(Optional.of(snapshot));
		when(results.lockSnapshot(runId)).thenReturn(Optional.of(snapshot));
		when(results.findConfirmed(snapshot)).thenReturn(new DrawConfirmation(runId, eventId, NOW, List.of(selected, unfilled)));
		when(publications.saveInitial(eventId, runId, List.of(resultId), NOW)).thenReturn(publication());
	}

	@Test
	@DisplayName("마감 +5분 경계에 승인 없이 SELECTED만 실제 공개 목록에 저장한다")
	void publishesAtBoundaryWithUnfilledSlot() {
		// when
		DrawPublication published = service.publishInitial(eventId).orElseThrow();
		// then
		assertThat(published.entries()).containsExactly(new DrawPublication.Entry(resultId, runId, null));
		assertThat(published.confirmedBy()).isNull();
		InOrder order = inOrder(snapshots, results, publications);
		order.verify(snapshots).lockEvent(eventId);
		order.verify(publications).findLatest(eventId);
		order.verify(publications).hasCancellationOrReplacement(eventId);
		order.verify(snapshots).findInitial(eventId);
		order.verify(results).lockSnapshot(runId);
		verify(publications).saveInitial(eventId, runId, List.of(resultId), NOW);
	}

	@Test
	@DisplayName("마감 +5분 직전에는 내부 확정 결과도 읽거나 공개하지 않는다")
	void blocksBeforeBoundary() {
		// given
		when(snapshots.lockEvent(eventId)).thenReturn(Optional.of(event(NOW.minusSeconds(300).plusNanos(1), EventStatus.CLOSED)));
		// when / then
		assertThat(service.publishInitial(eventId)).isEmpty();
		verifyNoInteractions(results);
		verify(publications, never()).saveInitial(any(), any(), anyList(), any());
	}

	@ParameterizedTest
	@EnumSource(value = DrawRunStatus.class, names = {"PREPARING", "READY", "RUNNING", "FAILED", "NO_ENTRIES", "NO_CANDIDATES"})
	@DisplayName("미확정·정상 종료를 공개로 처리하지 않는다")
	void blocksUnconfirmedAndTerminalRuns(DrawRunStatus status) {
		// given
		when(snapshots.findInitial(eventId)).thenReturn(Optional.of(new DrawSnapshot(runId, eventId,
				status, snapshot.fixedAt(), snapshot.algorithmVersion(), snapshot.rules(), snapshot.candidates())));
		// when / then
		assertThat(service.publishInitial(eventId)).isEmpty();
		verifyNoInteractions(results);
		verify(publications, never()).saveInitial(any(), any(), anyList(), any());
	}

	@Test
	@DisplayName("취소 또는 재추첨이 있으면 유지 명단만 먼저 공개하지 않는다")
	void delaysWholeInitialListForPendingReplacement() {
		// given
		when(publications.hasCancellationOrReplacement(eventId)).thenReturn(true);
		UUID replacement = UUID.randomUUID();
		when(publications.findReplacementRuns(eventId)).thenReturn(List.of(new DrawPublicationRun(replacement,
				eventId, 1, DrawRunStatus.PREPARING, null, List.of(), null)));
		when(publications.findCancellations(eventId)).thenReturn(List.of(new DrawPublicationCancellation(
				UUID.randomUUID(), resultId, replacement, "취소 사유")));
		// when / then
		assertThat(service.publishInitial(eventId)).isEmpty();
		verify(results).findConfirmed(snapshot);
		verify(publications, never()).saveInitial(any(), any(), anyList(), any());
	}

	@Test
	@DisplayName("최초 결과 준비 지연 후 재호출하면 실제 완료 시각에 공개한다")
	void publishesAfterDelayedPreparation() {
		// given
		when(snapshots.findInitial(eventId)).thenReturn(Optional.empty(), Optional.of(snapshot));
		// when / then
		assertThat(service.publishInitial(eventId)).isEmpty();
		assertThat(service.publishInitial(eventId).orElseThrow().publishedAt()).isEqualTo(NOW);
		verify(publications, times(1)).saveInitial(eventId, runId, List.of(resultId), NOW);
	}

	@Test
	@DisplayName("최초 공개 재요청은 저장된 버전·시각을 그대로 반환한다")
	void replaysExistingPublication() {
		// given
		DrawPublication stored = publication();
		when(publications.findLatest(eventId)).thenReturn(Optional.of(stored));
		// when / then
		assertThat(service.publishInitial(eventId)).contains(stored);
		assertThat(service.publishInitial(eventId)).contains(stored);
		verifyNoInteractions(results);
		verify(publications, never()).saveInitial(any(), any(), anyList(), any());
	}

	@Test
	@DisplayName("미충원 자리도 누락된 불완전 결과는 최초 공개를 거절한다")
	void rejectsPartialResult() {
		// given
		when(results.findConfirmed(snapshot)).thenReturn(new DrawConfirmation(runId, eventId, NOW, List.of(selected)));
		// when / then
		assertThatThrownBy(() -> service.publishInitial(eventId)).isInstanceOf(BusinessException.class);
		verify(publications, never()).saveInitial(any(), any(), anyList(), any());
	}

	@Test
	@DisplayName("같은 경품 자리의 중복 결과를 공개하지 않는다")
	void rejectsDuplicateSlot() {
		// given
		DrawResult duplicate = new DrawResult(UUID.randomUUID(), selected.getSelection(), NOW);
		when(results.findConfirmed(snapshot)).thenReturn(new DrawConfirmation(runId, eventId, NOW, List.of(selected, duplicate)));
		// when / then
		assertThatThrownBy(() -> service.publishInitial(eventId)).isInstanceOf(BusinessException.class);
		verify(publications, never()).saveInitial(any(), any(), anyList(), any());
	}

	@Test
	@DisplayName("취소 이벤트에는 최초 공개 버전을 만들지 않는다")
	void blocksCanceledEvent() {
		// given
		when(snapshots.lockEvent(eventId)).thenReturn(Optional.of(event(NOW.minusSeconds(300), EventStatus.CANCELED)));
		// when / then
		assertThat(service.publishInitial(eventId)).isEmpty();
		verifyNoInteractions(results);
	}

	@Test
	@DisplayName("실제 버전 간 변경 이력은 유지 자리를 제외하고 교체·미충원 자리의 전후를 보존한다")
	void comparesActualPublishedVersions() {
		// given: 재추첨 서비스 대역이 아니라 실제 공개 이력 조회의 대역이다.
		DrawPublication initial = publication();
		UUID replacementRun = UUID.randomUUID(), replacementResult = UUID.randomUUID(), replacementUser = UUID.randomUUID();
		UUID confirmationId = UUID.randomUUID();
		DrawPublication update = new DrawPublication(UUID.randomUUID(), eventId, replacementRun, 2,
				DrawPublicationType.UPDATE, "변경 사유", UUID.randomUUID(), NOW.minusSeconds(1), NOW,
				List.of(new DrawPublication.Entry(replacementResult, replacementRun, confirmationId)));
		DrawPublishedSlot before = new DrawPublishedSlot(prizeId, 1, resultId, userId);
		DrawPublishedSlot after = new DrawPublishedSlot(prizeId, 1, replacementResult, replacementUser);
		when(publications.findHistory(eventId)).thenReturn(List.of(initial, update));
		when(publications.findPublishedSlots(initial.id())).thenReturn(List.of(before));
		when(publications.findPublishedSlots(update.id())).thenReturn(List.of(after));
		// when
		List<DrawPublicationChange> changes = service.changes(eventId);
		// then
		assertThat(changes.getLast().publication()).isEqualTo(update);
		assertThat(changes.getLast().changes()).containsExactly(new DrawPublicationChange.SlotChange(
				prizeId, 1, resultId, userId, replacementResult, replacementUser));
		when(publications.findPublishedSlots(update.id())).thenReturn(List.of(before));
		assertThat(service.changes(eventId).getLast().changes()).isEmpty();
		when(publications.findPublishedSlots(update.id())).thenReturn(List.of());
		assertThat(service.changes(eventId).getLast().changes()).containsExactly(new DrawPublicationChange.SlotChange(
				prizeId, 1, resultId, userId, null, null));
	}

	private DrawSnapshotSource.Event event(Instant endsAt, EventStatus status) {
		return new DrawSnapshotSource.Event(eventId, EventType.NO_TICKET, false, endsAt, status, false, "NORMAL", List.of());
	}
	private DrawPublication publication() {
		return new DrawPublication(UUID.randomUUID(), eventId, runId, 1, DrawPublicationType.INITIAL,
				null, null, null, NOW, List.of(new DrawPublication.Entry(resultId, runId, null)));
	}
}
