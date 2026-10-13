package com.getddo.core.drawing.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.core.drawing.repository.DrawResultRepository;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;
import com.getddo.core.event.domain.EventStatus;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;

/** 최초 공개와 관리자 확인된 재추첨 명단 갱신을 처리하고 실제 공개 이력을 보존한다. */
@Service
@RequiredArgsConstructor
public class DrawPublicationService {
	private final DrawPublicationRepository publications;
	private final DrawSnapshotRepository snapshots;
	private final DrawResultRepository results;
	private final TimeProvider time;
	private final DrawPublicationNotificationService notifications;

	/**
	 * 마감 +5분 이후 저장된 최초 확정 결과만 공개한다. 승인·선정을 수행하지 않고 알림 작업 등록만 연결한다.
	 * 이벤트 → 실행 순서로 잠그며 목록 저장 실패 시 버전도 롤백한다.
	 * 공개 시각 전·미준비·정상 종료·취소·재추첨 대기는 빈 Optional을 반환한다.
	 * GD-86 스케줄러 연결은 별도 계약이며 이 메서드는 스케줄러를 등록하지 않는다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Optional<DrawPublication> publishInitial(UUID eventId) {
		return publish(eventId, true, null);
	}

	/** 관리자 확인과 같은 트랜잭션에서 전체 명단을 반영한다. 공개 전이면 예정 시각을 지킨다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Optional<DrawPublication> publishConfirmed(UUID eventId, UUID triggerRunId) {
		return publish(eventId, false, triggerRunId);
	}

	private Optional<DrawPublication> publish(UUID eventId, boolean initialOnly, UUID triggerRunId) {
		DrawSnapshotSource.Event event = snapshots.lockEvent(eventId)
				.orElseThrow(() -> new BusinessException(EVENT_NOT_FOUND));
		Optional<DrawPublication> existing = publications.findLatest(eventId);
		if (initialOnly && existing.isPresent()) return existing;
		Instant now = time.now();
		if (!canPublish(event) || now.isBefore(event.endsAt().plusSeconds(300))) return existing;
		boolean replacement = publications.hasCancellationOrReplacement(eventId);
		Optional<DrawSnapshot> initial = snapshots.findInitial(eventId);
		if (initial.isEmpty() || initial.get().status() != DrawRunStatus.CONFIRMED) return existing;
		DrawSnapshot snapshot = results.lockSnapshot(initial.get().runId())
				.orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (!eventId.equals(snapshot.eventId()) || snapshot.status() != DrawRunStatus.CONFIRMED) invalid();
		results.validateRelations(snapshot);
		DrawConfirmation confirmation = results.findConfirmed(snapshot);
		if (!eventId.equals(confirmation.getEventId()) || !snapshot.runId().equals(confirmation.getRunId())
				|| confirmation.getConfirmedAt().isAfter(now)) invalid();
		List<UUID> selected = validateInitial(snapshot, confirmation);
		if (!replacement) {
			if (existing.isPresent()) return existing;
			DrawPublication saved = publications.saveInitial(eventId, snapshot.runId(), selected, now);
			notifications.register(saved);
			return Optional.of(saved);
		}
		DrawPublicationComposition composed = DrawPublicationAssembler.compose(snapshot, confirmation,
				publications.findReplacementRuns(eventId), publications.findCancellations(eventId), triggerRunId, now);
		if (existing.isEmpty() && !composed.complete()) return Optional.empty();
		if (existing.isPresent()) {
			Set<DrawPublication.Entry> previous = new HashSet<>(existing.get().entries());
			if (previous.equals(new HashSet<>(composed.entries()))) return existing;
		}
		int revision = existing.map(value -> Math.addExact(value.revision(), 1)).orElse(1);
		DrawPublication saved = publications.savePublication(eventId, composed.sourceRunId(), revision,
				composed.reason(), composed.confirmation(), composed.entries(), now);
		publications.saveExecutionEvidence(saved.id(), eventId, composed.executions());
		notifications.register(saved);
		return Optional.of(saved);
	}

	/** 확인 전 관계 검증만 수행한다. 기존 실행·결과를 수정하거나 공개 버전을 만들지 않는다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public DrawPublicationRun validateReplacement(UUID eventId, UUID runId) {
		DrawSnapshotSource.Event event = snapshots.lockEvent(eventId)
				.orElseThrow(() -> new BusinessException(EVENT_NOT_FOUND));
		if (!canPublish(event)) throw new BusinessException(com.getddo.core.drawing.exception.DrawPublicationErrorCode.NOT_CONFIRMABLE);
		DrawSnapshot initial = snapshots.findInitial(eventId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		DrawSnapshot locked = results.lockSnapshot(initial.runId()).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (locked.status() != DrawRunStatus.CONFIRMED || !eventId.equals(locked.eventId())) invalid();
		results.validateRelations(locked);
		DrawConfirmation confirmed = results.findConfirmed(locked);
		if (!eventId.equals(confirmed.getEventId()) || !locked.runId().equals(confirmed.getRunId())) invalid();
		validateInitial(locked, confirmed);
		List<DrawPublicationRun> runs = publications.findReplacementRuns(eventId);
		DrawPublicationRun requested = runs.stream().filter(run -> run.runId().equals(runId)).findFirst()
				.orElseThrow(() -> new BusinessException(com.getddo.core.drawing.exception.DrawPublicationErrorCode.NOT_CONFIRMABLE));
		if (requested.status() != DrawRunStatus.CONFIRMED)
			throw new BusinessException(com.getddo.core.drawing.exception.DrawPublicationErrorCode.NOT_CONFIRMABLE);
		DrawPublicationAssembler.compose(locked, confirmed, runs, publications.findCancellations(eventId), runId, time.now());
		return requested;
	}

	private static boolean canPublish(DrawSnapshotSource.Event event) {
		return !event.deleted() && (event.status() == EventStatus.OPEN || event.status() == EventStatus.CLOSED
				|| event.status() == EventStatus.DRAW_CONFIRMED || event.status() == EventStatus.REDRAWING
				|| event.status() == EventStatus.PUBLISHED);
	}

	/** 공개 조회 계약과 분리된 내부 이력 조회이며 취소된 결과도 과거 버전에 남는다. */
	@Transactional(readOnly = true)
	public List<DrawPublication> history(UUID eventId) { return publications.findHistory(eventId); }

	/** 실행 상태가 아니라 해당 결과가 실제 포함됐던 버전으로 판정한다. */
	@Transactional(readOnly = true)
	public boolean wasPublished(UUID resultId) { return publications.wasPublished(resultId); }

	/** 실제 공개 버전끼리 비교하며 비공개 취소·미확인 실행을 변경 이력으로 노출하지 않는다. */
	@Transactional(readOnly = true)
	public List<DrawPublicationChange> changes(UUID eventId) {
		List<DrawPublicationChange> history = new ArrayList<>();
		List<DrawPublication> versions = publications.findHistory(eventId);
		if (versions.isEmpty()) return List.of();
		Set<UUID> ids = new HashSet<>();
		for (DrawPublication version : versions) ids.add(version.id());
		Map<UUID, List<DrawPublishedSlot>> slots = publications.findPublishedSlots(ids);
		Map<UUID, List<DrawPublicationExecution>> evidence = publications.findExecutionEvidence(ids);
		List<DrawPublishedSlot> before = List.of();
		for (DrawPublication publication : versions) {
			List<DrawPublishedSlot> after = slots.getOrDefault(publication.id(), List.of());
			DrawPublicationChange change = compare(publication, before, after);
			history.add(new DrawPublicationChange(publication, change.changes(),
					evidence.getOrDefault(publication.id(), List.of())));
			before = after;
		}
		return List.copyOf(history);
	}

	/** 최신 버전부터 페이징하되 각 버전을 실제 직전 버전과 비교한다. 페이지 경계도 비교에서 누락하지 않는다. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<DrawPublicationChange> changes(UUID eventId, PageQuery query) {
		PageResult<DrawPublication> page = publications.findHistory(eventId, query);
		if (page.getItems().isEmpty()) return new PageResult<>(List.of(), query.getPage(), query.getSize(), page.getTotalElements());
		List<DrawPublication> versions = page.getItems();
		Optional<DrawPublication> boundary = publications.findPrevious(eventId, versions.getLast().revision());
		Set<UUID> ids = new HashSet<>();
		for (DrawPublication version : versions) ids.add(version.id());
		boundary.ifPresent(version -> ids.add(version.id()));
		Map<UUID, List<DrawPublishedSlot>> slots = publications.findPublishedSlots(ids);
		Map<UUID, List<DrawPublicationExecution>> evidence = publications.findExecutionEvidence(ids);
		List<DrawPublicationChange> changes = new ArrayList<>();
		for (int index = 0; index < versions.size(); index++) {
			DrawPublication current = versions.get(index);
			DrawPublication previous = index + 1 < versions.size() ? versions.get(index + 1) : boundary.orElse(null);
			DrawPublicationChange change = compare(current, previous == null ? List.of() : slots.getOrDefault(previous.id(), List.of()),
					slots.getOrDefault(current.id(), List.of()));
			changes.add(new DrawPublicationChange(current, change.changes(), evidence.getOrDefault(current.id(), List.of())));
		}
		return new PageResult<>(changes, query.getPage(), query.getSize(), page.getTotalElements());
	}

	private static DrawPublicationChange compare(DrawPublication publication, List<DrawPublishedSlot> previous,
			List<DrawPublishedSlot> current) {
		Map<Slot, DrawPublishedSlot> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
		for (DrawPublishedSlot row : previous) before.put(new Slot(row.prizeId(), row.slotNumber()), row);
		for (DrawPublishedSlot row : current) after.put(new Slot(row.prizeId(), row.slotNumber()), row);
		Set<Slot> all = new LinkedHashSet<>(before.keySet());
		all.addAll(after.keySet());
		List<DrawPublicationChange.SlotChange> changes = new ArrayList<>();
		for (Slot slot : all) {
			DrawPublishedSlot old = before.get(slot), updated = after.get(slot);
			if (Objects.equals(old, updated)) continue;
			changes.add(new DrawPublicationChange.SlotChange(slot.prizeId(), slot.number(),
					old == null ? null : old.resultId(), old == null ? null : old.userId(),
					updated == null ? null : updated.resultId(), updated == null ? null : updated.userId()));
		}
		return new DrawPublicationChange(publication, changes);
	}

	@Getter
	@EqualsAndHashCode
	@Accessors(fluent = true)
	private static final class Slot {
		private final UUID prizeId;
		private final int number;

		private Slot(UUID prizeId, int number) {
			this.prizeId = prizeId;
			this.number = number;
		}
	}

	/** 미충원을 포함해 모든 자리가 완성돼야 최초 공개한다. 누락·사용자 중복·자리 중복을 거절한다. */
	private static List<UUID> validateInitial(DrawSnapshot snapshot, DrawConfirmation confirmation) {
		Map<UUID, DrawSnapshotSource.Prize> prizes = new HashMap<>();
		long slotCount = 0;
		for (DrawSnapshotSource.Prize prize : snapshot.rules().prizes()) {
			if (prize.winnerCount() < 1 || prizes.put(prize.id(), prize) != null) invalid();
			slotCount += prize.winnerCount();
		}
		if (slotCount != confirmation.getResults().size()) invalid();
		Set<UUID> ids = new HashSet<>();
		Set<UUID> users = new HashSet<>();
		Map<UUID, Set<Integer>> slots = new HashMap<>();
		List<UUID> selected = new ArrayList<>();
		for (DrawResult result : confirmation.getResults()) {
			DrawSelection selection = result.getSelection();
			DrawSnapshotSource.Prize prize = prizes.get(selection.getPrizeId());
			if (prize == null || prize.rank() != selection.getRank()
					|| selection.getSlotNumber() > prize.winnerCount() || !ids.add(result.getId())
					|| !slots.computeIfAbsent(prize.id(), ignored -> new HashSet<>()).add(selection.getSlotNumber())) invalid();
			if (selection.getResultType() == DrawSelectionResultType.SELECTED) {
				boolean candidateMatches = snapshot.candidates().stream().anyMatch(candidate ->
						candidate.id().equals(selection.getCandidateId()) && candidate.userId().equals(selection.getUserId()));
				if (!candidateMatches || !users.add(selection.getUserId())) invalid();
				selected.add(result.getId());
			}
		}
		return selected;
	}

	private static void invalid() { throw new BusinessException(INVALID_RUN); }
}
