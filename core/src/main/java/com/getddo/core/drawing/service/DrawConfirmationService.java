package com.getddo.core.drawing.service;

import java.util.ArrayList;
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
import com.getddo.core.drawing.domain.DrawResult;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSelection;
import com.getddo.core.drawing.domain.DrawSelectionResultType;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawSnapshotSource;
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

	/** 선정·영속성·시각 의존성을 주입하며 확정 업무의 트랜잭션은 confirm에서 관리한다. */
	public DrawConfirmationService(DrawResultRepository repository, DrawSnapshotRepository snapshots,
			DrawingSelectionService selection, TimeProvider time) {
		this.repository = repository;
		this.snapshots = snapshots;
		this.selection = selection;
		this.time = time;
	}

	/**
	 * 저장된 최초 실행 스냅샷으로 결과를 확정하고, 확정 재요청에는 기존 저장값을 반환한다.
	 * 이벤트 → 실행 순서로 잠그며 결과와 상태 변경은 서비스의 같은 트랜잭션에서 커밋한다.
	 * READ_COMMITTED는 이벤트 잠금 대기 후 이전 요청의 확정 결과를 읽기 위한 격리 수준이다.
	 * 확정 전 실패는 결과·상태·시각을 롤백하여 동일 READY 스냅샷으로 다시 시도할 수 있게 한다.
	 *
	 * @param runId 최초 추첨 실행 식별자
	 * @return 실제 DB에 저장된 결과 ID·순서·확정 시각
	 * @throws BusinessException 실행·스냅샷·배정 관계가 잘못됐거나 이벤트가 실행 불가한 경우
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public DrawConfirmation confirm(UUID runId) {
		UUID eventId = repository.findEventId(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		DrawSnapshotSource.Event event = snapshots.lockEvent(eventId)
				.orElseThrow(() -> new BusinessException(EVENT_NOT_FOUND));
		DrawSnapshot snapshot = repository.lockSnapshot(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (!eventId.equals(snapshot.eventId())) invalid();
		if (snapshot.status() == DrawRunStatus.CONFIRMED) {
			validateInput(snapshot);
			DrawConfirmation confirmed = repository.findConfirmed(snapshot);
			List<DrawSelection> storedSelections = new ArrayList<>();
			for (DrawResult result : confirmed.getResults()) {
				storedSelections.add(result.getSelection());
			}
			validateResults(snapshot, storedSelections);
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
		for (DrawSnapshotSource.Prize prize : snapshot.rules().prizes()) {
			targetPrizes.add(prize.id());
		}
		for (DrawSelection award : awards) {
			currentUsers.add(award.getUserId());
			// 최초 실행은 모든 자리가 대상이다. 유지 당첨자가 있는 경품을 덮어쓰지 않는다.
			if (targetPrizes.contains(award.getPrizeId())) invalid();
		}
		for (DrawSnapshot.Candidate candidate : snapshot.candidates()) {
			if (currentUsers.contains(candidate.userId())) {
				invalid();
			}
		}
		List<DrawSelection> selections = selection.select(toCandidates(snapshot), toPrizes(snapshot),
				snapshot.rules().weightingEnabled());
		validateResults(snapshot, selections);
		return repository.saveConfirmed(snapshot, selections, time.now());
	}

	/** 확정된 스냅샷의 버전·중복 후보·가중치·경품 조건을 검증한다. 최신 원본으로 입력을 교체하지 않는다. */
	private static void validateInput(DrawSnapshot snapshot) {
		if (snapshot.fixedAt() == null || !DrawSnapshotService.ALGORITHM_VERSION.equals(snapshot.algorithmVersion())
				|| snapshot.rules() == null || snapshot.rules().version() != 1
				|| snapshot.candidates().isEmpty() || snapshot.rules().prizes().isEmpty()) invalid();
		Set<UUID> participants = new HashSet<>();
		for (DrawSnapshot.Candidate candidate : snapshot.candidates()) {
			if (candidate.id() == null || candidate.userId() == null || candidate.participantId() == null
					|| !participants.add(candidate.participantId()) || candidate.ticketCount() < 0
					|| candidate.eligibilityEvidence() == null || candidate.eligibilityEvidence().excluded()
					|| !candidate.userId().equals(candidate.eligibilityEvidence().userId())) invalid();
		}
		try {
			DrawingSelectionValidator.validateCandidates(toCandidates(snapshot),
					snapshot.rules().weightingEnabled());
			DrawingSelectionValidator.validatePrizes(toPrizes(snapshot));
		} catch (IllegalArgumentException | NullPointerException invalidInput) {
			// 저장 스냅샷 검증은 선정기의 입력 오류를 일관된 도메인 오류로 보고한다.
			invalid();
		}
	}

	/** 저장된 후보의 식별자·사용자·가중치를 선정기 입력으로 변환한다. */
	private static List<DrawCandidate> toCandidates(DrawSnapshot snapshot) {
		List<DrawCandidate> candidates = new ArrayList<>();
		for (DrawSnapshot.Candidate candidate : snapshot.candidates()) {
			candidates.add(new DrawCandidate(candidate.id(), candidate.userId(), candidate.weight()));
		}
		return List.copyOf(candidates);
	}

	/** 저장된 경품의 식별자·등수·당첨 인원을 선정기 입력으로 변환한다. */
	private static List<DrawPrize> toPrizes(DrawSnapshot snapshot) {
		List<DrawPrize> prizes = new ArrayList<>();
		for (DrawSnapshotSource.Prize prize : snapshot.rules().prizes()) {
			prizes.add(new DrawPrize(prize.id(), prize.rank(), prize.winnerCount()));
		}
		return List.copyOf(prizes);
	}

	/** 저장 전에 자리·후보·순서와 상위 등수 우선 배정을 다시 검증한다. */
	static void validateResults(DrawSnapshot snapshot, List<DrawSelection> results) {
		int index = 0, order = 0;
		Set<UUID> users = new HashSet<>(), candidates = new HashSet<>();
		Map<UUID, UUID> candidateUsers = new HashMap<>();
		for (DrawSnapshot.Candidate candidate : snapshot.candidates()) {
			candidateUsers.put(candidate.id(), candidate.userId());
		}
		List<DrawSnapshotSource.Prize> orderedPrizes = new ArrayList<>(snapshot.rules().prizes());
		orderedPrizes.sort(Comparator.comparingInt(DrawSnapshotSource.Prize::rank));
		for (DrawSnapshotSource.Prize prize : orderedPrizes) {
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

	/** 잘못된 실행·스냅샷·결과를 동일한 도메인 오류로 거절하여 서비스 트랜잭션을 롤백한다. */
	private static void invalid() { throw new BusinessException(INVALID_RUN); }
}
