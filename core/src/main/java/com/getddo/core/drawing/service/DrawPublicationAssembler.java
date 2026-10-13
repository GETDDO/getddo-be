package com.getddo.core.drawing.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.EqualsAndHashCode;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.*;

import static com.getddo.core.drawing.exception.DrawPublicationErrorCode.INVALID_PUBLICATION;

/** 저장된 결과를 취소 연결과 실제 자리로 병합한다. 선정·취소·실행 생성·실패 재개를 하지 않는다. */
public final class DrawPublicationAssembler {
	/** 명단 구성은 정적 함수로만 제공하므로 인스턴스 생성을 막는다. */
	private DrawPublicationAssembler() { }

	/**
	 * 내부 확정 명단과 관리자 확인을 마친 공개 가능 명단을 각각 구성한다.
	 * 최초 공개는 모든 연결 실행의 준비·확인을 요구한다. 이후 갱신은 확인된 실행만 반영한다.
	 * 미확인 중간 결과에 연결된 후속 실행은 그 선행 확인까지 공개하지 않는다.
	 */
	public static DrawPublicationComposition compose(DrawSnapshot initial, DrawConfirmation original,
			List<DrawPublicationRun> replacements, List<DrawPublicationCancellation> cancellations,
			UUID triggerRunId, Instant now) {
		if (!initial.eventId().equals(original.getEventId()) || !initial.runId().equals(original.getRunId())
				|| original.getConfirmedAt().isAfter(now)) invalid();
		Map<UUID, DrawSnapshotSource.Prize> prizes = new HashMap<>();
		for (DrawSnapshotSource.Prize prize : initial.rules().prizes()) prizes.put(prize.id(), prize);
		Map<UUID, UUID> candidates = new HashMap<>();
		for (DrawSnapshot.Candidate candidate : initial.candidates()) candidates.put(candidate.id(), candidate.userId());
		Map<Slot, Included> internal = new LinkedHashMap<>();
		Set<UUID> resultIds = new HashSet<>();
		Set<UUID> historicalUsers = new HashSet<>();
		Map<UUID, DrawResult> knownResults = new HashMap<>();
		for (DrawResult result : original.getResults()) {
			validateResult(result, prizes, candidates, resultIds);
			knownResults.put(result.getId(), result);
			if (result.getSelection().getResultType() == DrawSelectionResultType.SELECTED
					&& !historicalUsers.add(result.getSelection().getUserId())) invalid();
			if (internal.put(slot(result), new Included(result, original.getRunId(), null)) != null) invalid();
		}
		long totalSlots = prizes.values().stream().mapToLong(DrawSnapshotSource.Prize::winnerCount).sum();
		if (internal.size() != totalSlots) invalid();
		Map<Slot, Included> visible = new LinkedHashMap<>(internal);
		List<DrawPublicationRun> ordered = replacements.stream()
				.sorted(Comparator.comparingInt(DrawPublicationRun::getRunNumber)).toList();
		Map<UUID, List<DrawPublicationCancellation>> linked = new HashMap<>();
		Set<UUID> runIds = new HashSet<>(), cancellationIds = new HashSet<>(), canceledResults = new HashSet<>();
		Set<Integer> runNumbers = new HashSet<>();
		for (DrawPublicationRun run : ordered) {
			if (!initial.eventId().equals(run.getEventId()) || run.getRunNumber() < 1
					|| !runIds.add(run.getRunId()) || !runNumbers.add(run.getRunNumber())) invalid();
		}
		boolean complete = true;
		for (DrawPublicationCancellation cancellation : cancellations) {
			if (!cancellationIds.add(cancellation.getId()) || !canceledResults.add(cancellation.getResultId())
					|| cancellation.getReason() == null || cancellation.getReason().isBlank()) invalid();
			if (cancellation.getReplacementRunId() == null) { complete = false; continue; }
			if (!runIds.contains(cancellation.getReplacementRunId())) invalid();
			linked.computeIfAbsent(cancellation.getReplacementRunId(), ignored -> new ArrayList<>()).add(cancellation);
		}
		DrawPublicationRun source = null;
		for (DrawPublicationRun run : ordered) {
			List<DrawPublicationCancellation> causes = linked.getOrDefault(run.getRunId(), List.of());
			if (causes.isEmpty()) invalid();
			Map<Slot, UUID> targets = new LinkedHashMap<>();
			for (DrawPublicationCancellation cancellation : causes) {
				Included canceled = internal.values().stream()
						.filter(value -> value.result.getId().equals(cancellation.getResultId())).findFirst().orElse(null);
				if (canceled == null || canceled.result.getSelection().getResultType() != DrawSelectionResultType.SELECTED
						|| targets.put(slot(canceled.result), cancellation.getResultId()) != null) invalid();
			}
			if (run.getStatus() != DrawRunStatus.CONFIRMED) {
				if (run.getConfirmation() != null || !run.getResults().isEmpty()
						|| run.getStatus() == DrawRunStatus.NO_ENTRIES || run.getStatus() == DrawRunStatus.NO_CANDIDATES) invalid();
				complete = false;
				continue;
			}
			if (run.getConfirmedAt() == null || run.getConfirmedAt().isAfter(now) || run.getResults().size() != targets.size()) invalid();
			Map<Slot, Included> additions = new LinkedHashMap<>();
			for (DrawResult result : run.getResults()) {
				validateResult(result, prizes, candidates, resultIds);
				knownResults.put(result.getId(), result);
				if (result.getSelection().getResultType() == DrawSelectionResultType.SELECTED
						&& !historicalUsers.add(result.getSelection().getUserId())) invalid();
				if (!targets.containsKey(slot(result)) || additions.put(slot(result),
						new Included(result, run.getRunId(), run.getConfirmation())) != null) invalid();
			}
			internal.putAll(additions);
			validateUsers(internal);
			DrawResultConfirmation checked = run.getConfirmation();
			if (checked == null) { complete = false; continue; }
			if (!run.getRunId().equals(checked.getRunId()) || checked.getConfirmedAt().isBefore(run.getConfirmedAt())
					|| checked.getConfirmedAt().isAfter(now)) invalid();
			boolean dependenciesVisible = targets.entrySet().stream().allMatch(target ->
					visible.containsKey(target.getKey()) && visible.get(target.getKey()).result.getId().equals(target.getValue()));
			if (!dependenciesVisible) { complete = false; continue; }
			visible.putAll(additions);
			validateUsers(visible);
			if (source == null || !Objects.equals(source.getRunId(), triggerRunId)) source = run;
		}
		for (UUID canceledId : canceledResults) {
			DrawResult canceled = knownResults.get(canceledId);
			if (canceled == null || canceled.getSelection().getResultType() != DrawSelectionResultType.SELECTED) invalid();
		}
		List<DrawPublication.Entry> entries = visible.values().stream()
				.filter(value -> value.result.getSelection().getResultType() == DrawSelectionResultType.SELECTED)
				.map(value -> new DrawPublication.Entry(value.result.getId(), value.runId,
						value.confirmation == null ? null : value.confirmation.getId())).toList();
		// visible에는 미충원 자리도 남는다. 확인이 있는 항목의 실행만 근거에 포함한다.
		Set<UUID> visibleRuns = new HashSet<>();
		for (Included included : visible.values()) if (included.confirmation != null) visibleRuns.add(included.runId);
		List<DrawPublicationExecution> evidence = ordered.stream().filter(run -> visibleRuns.contains(run.getRunId()))
				.map(run -> new DrawPublicationExecution(run.getRunId(), run.getConfirmation().getId(), run.getConfirmation().getConfirmedBy(),
						run.getConfirmation().getConfirmedAt(), linked.getOrDefault(run.getRunId(), List.of()))).toList();
		String reason = source == null ? null : linked.get(source.getRunId()).stream()
				.map(DrawPublicationCancellation::getReason).distinct().collect(java.util.stream.Collectors.joining("\n"));
		return new DrawPublicationComposition(complete, source == null ? initial.runId() : source.getRunId(),
				reason, source == null ? null : source.getConfirmation(), entries, evidence);
	}

	/** 경품 자리·등수·결과 ID 중복 및 선정 후보와 사용자의 연결을 검증한다. */
	private static void validateResult(DrawResult result, Map<UUID, DrawSnapshotSource.Prize> prizes,
			Map<UUID, UUID> candidates, Set<UUID> resultIds) {
		DrawSelection selection = result.getSelection();
		DrawSnapshotSource.Prize prize = prizes.get(selection.getPrizeId());
		if (prize == null || selection.getSlotNumber() > prize.winnerCount() || prize.rank() != selection.getRank()
				|| !resultIds.add(result.getId())) invalid();
		if (selection.getResultType() == DrawSelectionResultType.SELECTED
				&& !Objects.equals(candidates.get(selection.getCandidateId()), selection.getUserId())) invalid();
	}

	/** 현재 구성 명단에 동일한 선정 사용자가 여러 자리로 포함되지 않았는지 검증한다. */
	private static void validateUsers(Map<Slot, Included> list) {
		Set<UUID> users = new HashSet<>();
		for (Included value : list.values()) {
			if (value.result.getSelection().getResultType() == DrawSelectionResultType.SELECTED
					&& !users.add(value.result.getSelection().getUserId())) invalid();
		}
	}

	/** 결과의 경품 식별자와 자리 번호를 비교 키로 구성한다. */
	private static Slot slot(DrawResult result) {
		return new Slot(result.getSelection().getPrizeId(), result.getSelection().getSlotNumber());
	}
	/** 공개 명단의 연결 또는 구성 불일치를 업무 예외로 거절한다. */
	private static void invalid() { throw new BusinessException(INVALID_PUBLICATION); }

	@EqualsAndHashCode
	private static final class Slot {
		private final UUID prizeId;
		private final int number;
		/** 동일 경품의 동일 자리를 식별하는 비교 키를 구성한다. */
		private Slot(UUID prizeId, int number) { this.prizeId = prizeId; this.number = number; }
	}
	private static final class Included {
		private final DrawResult result;
		private final UUID runId;
		private final DrawResultConfirmation confirmation;
		/** 명단 결과에 원본 실행과 선택적인 관리자 확인 기록을 연결한다. */
		private Included(DrawResult result, UUID runId, DrawResultConfirmation confirmation) {
			this.result = result; this.runId = runId; this.confirmation = confirmation;
		}
	}
}
