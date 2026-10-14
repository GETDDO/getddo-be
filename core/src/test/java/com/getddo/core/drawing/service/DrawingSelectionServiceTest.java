package com.getddo.core.drawing.service;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.getddo.core.drawing.domain.DrawCandidate;
import com.getddo.core.drawing.domain.DrawPrize;
import com.getddo.core.drawing.domain.DrawSelection;
import com.getddo.core.drawing.domain.DrawSelectionResultType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DrawingSelectionServiceTest {

	@Test
	void selectsHigherRanksFirstAndRemovesWinnersAcrossPrizes() {
		List<BigInteger> requestedBounds = new ArrayList<>();
		DrawingSelectionService service = new DrawingSelectionService(rolls(requestedBounds, 1, 1, 0));
		List<DrawCandidate> candidates = new ArrayList<>(List.of(candidate(1, 1), candidate(2, 1), candidate(3, 1)));
		DrawPrize second = prize(2, 2, 2);
		DrawPrize first = prize(1, 1, 1);

		List<DrawSelection> results = service.select(candidates, List.of(second, first), false);

		assertThat(results).containsExactly(
				DrawSelection.selected(first, 1, 1, candidates.get(1)),
				DrawSelection.selected(second, 1, 2, candidates.get(2)),
				DrawSelection.selected(second, 2, 3, candidates.get(0)));
		assertThat(requestedBounds).containsExactly(BigInteger.valueOf(3), BigInteger.valueOf(2), BigInteger.ONE);
		assertThat(candidates).hasSize(3);
		assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(results::clear);
	}

	@Test
	void weightedIntervalsGiveOneOfFourValuesToFirstCandidate() {
		List<DrawCandidate> candidates = List.of(candidate(1, 1), candidate(2, 3));
		DrawPrize prize = prize(1, 1, 1);

		for (int value = 0; value < 4; value++) {
			int roll = value;
			DrawingSelectionService service = new DrawingSelectionService(bound -> {
				assertThat(bound).isEqualTo(BigInteger.valueOf(4));
				return BigInteger.valueOf(roll);
			});
			UUID expected = value == 0 ? candidates.get(0).getCandidateId() : candidates.get(1).getCandidateId();
			assertThat(service.select(candidates, List.of(prize), true).get(0).getCandidateId()).isEqualTo(expected);
		}
	}

	@Test
	void handlesTotalWeightBeyondLongRange() {
		List<DrawCandidate> candidates = List.of(candidate(1, Long.MAX_VALUE), candidate(2, Long.MAX_VALUE));
		DrawingSelectionService service = new DrawingSelectionService(bound -> {
			assertThat(bound).isEqualTo(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO));
			return BigInteger.valueOf(Long.MAX_VALUE);
		});

		assertThat(service.select(candidates, List.of(prize(1, 1, 1)), true).get(0).getCandidateId())
				.isEqualTo(candidates.get(1).getCandidateId());
	}

	@Test
	void leavesLowerRankSlotsUnfilledWhenCandidatesRunOut() {
		List<DrawCandidate> candidates = List.of(candidate(1, 1), candidate(2, 1));
		DrawPrize first = prize(1, 1, 2);
		DrawPrize second = prize(2, 2, 2);
		DrawingSelectionService service = new DrawingSelectionService(rolls(new ArrayList<>(), 0, 0));

		List<DrawSelection> results = service.select(candidates, List.of(second, first), false);

		assertThat(results).containsExactly(
				DrawSelection.selected(first, 1, 1, candidates.get(0)),
				DrawSelection.selected(first, 2, 2, candidates.get(1)),
				DrawSelection.unfilled(second, 1),
				DrawSelection.unfilled(second, 2));
		assertThat(results.get(2).getResultType()).isEqualTo(DrawSelectionResultType.UNFILLED);
		assertThat(results.get(2).getSelectionOrder()).isNull();
	}

	@Test
	void rejectsInvalidCandidatesAndPrizeConfiguration() {
		DrawCandidate first = candidate(1, 1);
		DrawCandidate sameUser = new DrawCandidate(id(9), first.getUserId(), 1);
		DrawCandidate sameCandidate = new DrawCandidate(first.getCandidateId(), id(9), 1);
		DrawPrize firstPrize = prize(1, 1, 1);
		DrawingSelectionService service = new DrawingSelectionService(bound -> BigInteger.ZERO);

		assertThatIllegalArgumentException().isThrownBy(() -> service.select(List.of(), List.of(firstPrize), false));
		assertThatIllegalArgumentException().isThrownBy(() -> service.select(List.of(first), List.of(), false));
		assertThatIllegalArgumentException().isThrownBy(() -> service.select(List.of(first, sameUser), List.of(firstPrize), false));
		assertThatIllegalArgumentException().isThrownBy(() -> service.select(List.of(first, sameCandidate), List.of(firstPrize), false));
		assertThatIllegalArgumentException().isThrownBy(() -> service.select(List.of(candidate(1, 2)), List.of(firstPrize), false));
		assertThatIllegalArgumentException().isThrownBy(() -> service.select(List.of(first), List.of(firstPrize, prize(2, 1, 1)), false));
		assertThatIllegalArgumentException().isThrownBy(() -> service.select(List.of(first), List.of(firstPrize, prize(1, 2, 1)), false));
		assertThatIllegalArgumentException().isThrownBy(() -> service.select(List.of(first), List.of(prize(1, 1, Integer.MAX_VALUE), prize(2, 2, 1)), false));
		assertThatIllegalArgumentException().isThrownBy(() -> new DrawCandidate(id(1), id(2), 0));
		assertThatIllegalArgumentException().isThrownBy(() -> new DrawPrize(id(1), 1, 0));
		assertThatNullPointerException().isThrownBy(() -> service.select(null, List.of(firstPrize), false));
	}

	@Test
	void rejectsOutOfRangeRandomResult() {
		DrawingSelectionService service = new DrawingSelectionService(bound -> bound);
		assertThatIllegalStateException().isThrownBy(() -> service.select(
				List.of(candidate(1, 1)), List.of(prize(1, 1, 1)), false));
	}

	private static DrawRandom rolls(List<BigInteger> requestedBounds, long... values) {
		Queue<BigInteger> rolls = new ArrayDeque<>();
		for (long value : values) {
			rolls.add(BigInteger.valueOf(value));
		}
		return bound -> {
			requestedBounds.add(bound);
			return rolls.remove();
		};
	}

	private static DrawCandidate candidate(long number, long weight) {
		return new DrawCandidate(id(number), id(number + 100), weight);
	}

	private static DrawPrize prize(long number, int rank, int winnerCount) {
		return new DrawPrize(id(number + 200), rank, winnerCount);
	}

	private static UUID id(long number) {
		return new UUID(0, number);
	}
}
