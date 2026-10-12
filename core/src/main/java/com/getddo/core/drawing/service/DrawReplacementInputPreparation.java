package com.getddo.core.drawing.service;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.*;

import static com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN;

/** GD-87이 이벤트·실행 잠금과 시도 소유권을 확보한 준비 트랜잭션에 참여한다. */
@Service
@RequiredArgsConstructor
public class DrawReplacementInputPreparation implements DrawInputPreparation {
	private final DrawSnapshotRepository snapshots;
	private final DrawResultRepository results;
	private final AwardCancellationRepository cancellations;
	private final ReplacementDrawRepository replacements;
	private final ReplacementDrawInputBuilder builder;
	private final TimeProvider time;

	@Override public boolean supports(DrawType type) { return type == DrawType.REPLACEMENT; }

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public DrawSnapshot prepare(UUID runId, UUID attemptId) {
		if (attemptId == null) throw new BusinessException(INVALID_RUN);
		results.validateAttempt(runId, attemptId);
		DrawSnapshot registered = snapshots.findRegistered(runId).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (registered.drawType() != DrawType.REPLACEMENT) throw new BusinessException(INVALID_RUN);
		if (registered.fixedAt() != null) return registered;
		DrawSnapshot original = snapshots.findInitial(registered.eventId()).orElseThrow(() -> new BusinessException(INVALID_RUN));
		List<DrawResult> canceled = replacements.findCanceledResults(registered.eventId(), original);
		List<AwardCancellation> linked = cancellations.findByReplacementRunId(runId);
		// 개별 취소 한 건이 실행 하나를 등록한다. 일괄 취소 기능을 추가하지 않는다.
		if (linked.size() != 1) throw new BusinessException(INVALID_RUN);
		DrawResult target = canceled.stream().filter(result -> result.getId().equals(linked.getFirst().drawResultId()))
				.findFirst().orElseThrow(() -> new BusinessException(INVALID_RUN));
		ReplacementDrawInput input = builder.build(original, target,
				replacements.findCurrentResults(registered.eventId(), original), canceled);
		DrawSnapshot.Rules rules = input.originalRules();
		DrawSnapshot.Rules fixedRules = new DrawSnapshot.Rules(2, rules.weightingEnabled(), rules.eventType(), rules.membershipRule(),
				rules.bronzeWeight(), rules.silverWeight(), rules.goldWeight(), rules.prizes(), input.originalRunId(),
				input.vacantSlots().stream().map(slot -> new DrawSnapshot.Slot(slot.prizeId(), slot.rank(), slot.slotNumber())).toList(),
				input.retainedResults().stream().map(result -> new DrawSnapshot.RetainedAward(result.getId(),
						result.getSelection().getUserId(), result.getSelection().getPrizeId(), result.getSelection().getSlotNumber())).toList());
		return snapshots.saveRegisteredReplacement(new DrawSnapshot(runId, registered.eventId(), DrawRunStatus.READY,
				time.now(), original.algorithmVersion(), fixedRules, input.candidates(), DrawType.REPLACEMENT));
	}
}
