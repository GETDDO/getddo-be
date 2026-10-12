package com.getddo.core.drawing.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.*;

import static org.assertj.core.api.Assertions.*;

class ReplacementDrawInputBuilderTest {
	private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
	private final UUID prizeId = UUID.randomUUID();
	private final ReplacementDrawInputBuilder builder = new ReplacementDrawInputBuilder();
	private final DrawSnapshot.Candidate canceled = candidate(9);
	private final DrawSnapshot.Candidate retained = candidate(25);
	private final DrawSnapshot.Candidate loser = candidate(3);

	@Test
	@DisplayName("유지 당첨과 최초 가중치·근거를 보존하며 실제 빈 자리만 준비한다")
	void preservesOriginalWeightEvidenceAndActualVacantSlot() {
		DrawResult canceledResult = result(canceled, 2);
		DrawResult retainedResult = result(retained, 1);
		DrawSnapshot original = original(List.of(canceled, retained, loser));
		ReplacementDrawInput input = builder.build(original, canceledResult,
				List.of(retainedResult), List.of(canceledResult));
		assertThat(input.candidates()).containsExactly(loser);
		assertThat(input.candidates().getFirst()).isSameAs(loser);
		assertThat(input.candidates().getFirst().weight()).isEqualTo(3);
		assertThat(input.vacantSlots()).containsExactly(new ReplacementDrawInput.Slot(prizeId, 1, 2));
		assertThat(input.retainedResults()).containsExactly(retainedResult);
		assertThat(input.originalRules()).isSameAs(original.rules());
	}

	@Test
	@DisplayName("이전 재추첨에서 취소된 당첨자도 후보에서 제거한다")
	void excludesWinnersCanceledInEarlierReplacementRuns() {
		DrawResult canceledResult = result(canceled, 2);
		DrawResult earlierCancellation = result(loser, 2);
		ReplacementDrawInput input = builder.build(original(List.of(canceled, retained, loser)), canceledResult,
				List.of(result(retained, 1)), List.of(canceledResult, earlierCancellation));
		assertThat(input.candidates()).isEmpty();
		assertThat(input.vacantSlots()).hasSize(1);
	}

	@Test
	@DisplayName("남은 후보가 없어도 미충원 확정에 필요한 빈 자리를 준비한다")
	void allowsNoCandidatesForUnfilledConfirmation() {
		DrawResult canceledResult = result(canceled, 2);
		ReplacementDrawInput input = builder.build(original(List.of(canceled, retained)), canceledResult,
				List.of(result(retained, 1)), List.of(canceledResult));
		assertThat(input.candidates()).isEmpty();
		assertThat(input.vacantSlots()).containsExactly(new ReplacementDrawInput.Slot(prizeId, 1, 2));
	}

	@Test
	@DisplayName("유지 당첨자가 차지한 자리는 재추첨 대상으로 만들지 않는다")
	void rejectsVacancyOccupiedByRetainedWinner() {
		DrawResult canceledResult = result(canceled, 2);
		assertThatThrownBy(() -> builder.build(original(List.of(canceled, retained)), canceledResult,
				List.of(result(retained, 2)), List.of(canceledResult))).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("취소 이력에 없는 결과는 빈 자리 대상으로 만들지 않는다")
	void rejectsTargetNotInCancellationHistory() {
		assertThatThrownBy(() -> builder.build(original(List.of(canceled, retained)), result(canceled, 2),
				List.of(result(retained, 1)), List.of())).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("후보와 결과 사용자의 불일치를 거절한다")
	void rejectsWrongCandidateUserRelation() {
		DrawResult wrong = new DrawResult(UUID.randomUUID(), new DrawSelection(prizeId, 1, 2, 1,
				canceled.id(), UUID.randomUUID(), DrawSelectionResultType.SELECTED), NOW);
		assertThatThrownBy(() -> builder.build(original(List.of(canceled, retained)), wrong,
				List.of(result(retained, 1)), List.of(wrong))).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("미충원 결과의 취소를 거절한다")
	void rejectsUnfilledCancellation() {
		DrawResult unfilled = new DrawResult(UUID.randomUUID(),
				DrawSelection.unfilled(new DrawPrize(prizeId, 1, 2), 2), NOW);
		assertThatThrownBy(() -> builder.build(original(List.of(canceled, retained)), unfilled,
				List.of(result(retained, 1)), List.of(unfilled))).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("입력 목록을 외부에서 변경할 수 없다")
	void outputListsAreImmutable() {
		DrawResult canceledResult = result(canceled, 2);
		ReplacementDrawInput input = builder.build(original(List.of(canceled, retained, loser)), canceledResult,
				List.of(result(retained, 1)), List.of(canceledResult));
		assertThatThrownBy(() -> input.candidates().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> input.vacantSlots().clear()).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> input.retainedResults().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	private DrawSnapshot original(List<DrawSnapshot.Candidate> candidates) {
		return new DrawSnapshot(UUID.randomUUID(), UUID.randomUUID(), DrawRunStatus.CONFIRMED, NOW,
				DrawSnapshotService.ALGORITHM_VERSION,
				new DrawSnapshot.Rules(1, true, "TICKET", "VIP", 1, 3, 5,
						List.of(new DrawSnapshotSource.Prize(prizeId, 1, 2))), candidates);
	}

	private DrawResult result(DrawSnapshot.Candidate candidate, int slot) {
		return new DrawResult(UUID.randomUUID(), new DrawSelection(prizeId, 1, slot, slot,
				candidate.id(), candidate.userId(), DrawSelectionResultType.SELECTED), NOW);
	}

	private static DrawSnapshot.Candidate candidate(long weight) {
		UUID userId = UUID.randomUUID();
		return new DrawSnapshot.Candidate(UUID.randomUUID(), UUID.randomUUID(), userId, 1, weight,
				new DrawSnapshot.EntryEvidence(1, 0, 1, 0, List.of()),
				new DrawSnapshot.EligibilityEvidence(1, userId, "VIP", "USER", false, NOW));
	}
}
