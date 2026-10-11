package com.getddo.core.drawing.service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.DrawCandidate;
import com.getddo.core.drawing.domain.DrawConfirmation;
import com.getddo.core.drawing.domain.DrawPrize;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSelection;
import com.getddo.core.drawing.domain.DrawSelectionResultType;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.repository.DrawResultRepository;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;
import com.getddo.core.event.domain.EventStatus;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;

/** READY 스냅샷의 결과와 CONFIRMED 상태를 원자적으로 저장한다. */
@Service
public class DrawConfirmationService {
	private final DrawResultRepository repository;
	private final DrawSnapshotRepository snapshots;
	private final DrawingSelectionService selection;
	private final TimeProvider time;

	public DrawConfirmationService(DrawResultRepository repository, DrawSnapshotRepository snapshots,
			DrawingSelectionService selection, TimeProvider time) {
		this.repository = repository;
		this.snapshots = snapshots;
		this.selection = selection;
		this.time = time;
	}

	/** 같은 이벤트의 확정을 직렬화하며 확정된 실행에는 난수를 다시 사용하지 않는다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public DrawConfirmation confirm(UUID runId) {
		UUID eventId = repository.findEventId(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		var event = snapshots.lockEvent(eventId).orElseThrow(() -> new BusinessException(EVENT_NOT_FOUND));
		DrawSnapshot snapshot = repository.lockSnapshot(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (!eventId.equals(snapshot.eventId())) invalid();
		if (snapshot.status() == DrawRunStatus.CONFIRMED) {
			validateInput(snapshot);
			DrawConfirmation confirmed = repository.findConfirmed(snapshot);
			validateResults(snapshot, confirmed.getResults().stream().map(result -> result.getSelection()).toList());
			return confirmed;
		}
		if (snapshot.status() != DrawRunStatus.READY || repository.hasResults(runId)) invalid();
		if (event.deleted() || (event.status() != EventStatus.OPEN && event.status() != EventStatus.CLOSED)
				|| time.now().isBefore(event.endsAt().plusSeconds(300))) {
			throw new BusinessException(NOT_EXECUTABLE);
		}
		validateInput(snapshot);
		repository.validateRelations(snapshot);
		List<DrawSelection> awards = repository.findCurrentAwards(eventId);
		Set<UUID> currentUsers = new HashSet<>();
		Set<UUID> targetPrizes = new HashSet<>();
		snapshot.rules().prizes().forEach(prize -> targetPrizes.add(prize.id()));
		for (DrawSelection award : awards) {
			currentUsers.add(award.getUserId());
			// 최초 실행은 모든 자리가 대상이다. 유지 당첨자가 있는 경품을 덮어쓰지 않는다.
			if (targetPrizes.contains(award.getPrizeId())) invalid();
		}
		if (snapshot.candidates().stream().anyMatch(candidate -> currentUsers.contains(candidate.userId()))) invalid();
		List<DrawSelection> selections = selection.select(snapshot.candidates().stream()
				.map(candidate -> new DrawCandidate(candidate.id(), candidate.userId(), candidate.weight())).toList(),
				snapshot.rules().prizes().stream().map(prize -> new DrawPrize(prize.id(), prize.rank(), prize.winnerCount())).toList(),
				snapshot.rules().weightingEnabled());
		validateResults(snapshot, selections);
		return repository.saveConfirmed(snapshot, selections, time.now());
	}

	private static void validateInput(DrawSnapshot snapshot) {
		if (snapshot.fixedAt() == null || !DrawSnapshotService.ALGORITHM_VERSION.equals(snapshot.algorithmVersion())
				|| snapshot.rules() == null || snapshot.rules().version() != 1
				|| snapshot.candidates().isEmpty() || snapshot.rules().prizes().isEmpty()) invalid();
		Set<UUID> users = new HashSet<>(), ids = new HashSet<>(), participants = new HashSet<>(), prizes = new HashSet<>();
		Set<Integer> ranks = new HashSet<>();
		long slots = 0;
		for (var candidate : snapshot.candidates()) {
			if (candidate.id() == null || candidate.userId() == null || candidate.participantId() == null
					|| !ids.add(candidate.id()) || !users.add(candidate.userId()) || !participants.add(candidate.participantId())
					|| candidate.weight() < 1 || candidate.ticketCount() < 0
					|| (!snapshot.rules().weightingEnabled() && candidate.weight() != 1)
					|| candidate.eligibilityEvidence() == null || candidate.eligibilityEvidence().excluded()
					|| !candidate.userId().equals(candidate.eligibilityEvidence().userId())) invalid();
		}
		for (var prize : snapshot.rules().prizes()) {
			if (prize.id() == null || !prizes.add(prize.id()) || !ranks.add(prize.rank())
					|| prize.rank() < 1 || prize.winnerCount() < 1) invalid();
			slots += prize.winnerCount();
			if (slots > Integer.MAX_VALUE) invalid();
		}
	}

	/** 저장 전에 자리·후보·순서와 상위 등수 우선 배정을 다시 검증한다. */
	static void validateResults(DrawSnapshot snapshot, List<DrawSelection> results) {
		int index = 0, order = 0;
		Set<UUID> users = new HashSet<>(), candidates = new HashSet<>();
		Map<UUID, UUID> candidateUsers = new HashMap<>();
		snapshot.candidates().forEach(candidate -> candidateUsers.put(candidate.id(), candidate.userId()));
		for (var prize : snapshot.rules().prizes().stream()
				.sorted(Comparator.comparingInt(p -> p.rank())).toList()) {
			for (int slot = 1; slot <= prize.winnerCount(); slot++) {
				if (index >= results.size()) invalid();
				DrawSelection result = results.get(index++);
				if (!prize.id().equals(result.getPrizeId()) || result.getRank() != prize.rank()
						|| result.getSlotNumber() != slot) invalid();
				if (result.getResultType() == DrawSelectionResultType.SELECTED) {
					if (++order != result.getSelectionOrder() || !users.add(result.getUserId())
							|| !candidates.add(result.getCandidateId())
							|| !result.getUserId().equals(candidateUsers.get(result.getCandidateId()))) invalid();
				} else if (order != snapshot.candidates().size()) invalid();
			}
		}
		if (index != results.size() || order != Math.min(snapshot.candidates().size(), index)) invalid();
	}

	private static void invalid() { throw new BusinessException(INVALID_RUN); }
}
