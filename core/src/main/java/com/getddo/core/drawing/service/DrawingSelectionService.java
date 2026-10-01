package com.getddo.core.drawing.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawCandidate;
import com.getddo.core.drawing.domain.DrawPrize;
import com.getddo.core.drawing.domain.DrawSelection;

/** 확정된 후보·경품만 입력받아 상위 등수부터 당첨자를 선정한다. */
public final class DrawingSelectionService {

	private final DrawRandom random;

	public DrawingSelectionService() {
		this(new SecureDrawRandom());
	}

	/** 테스트에서는 제어 가능한 난수를 주입한다. */
	public DrawingSelectionService(DrawRandom random) {
		this.random = Objects.requireNonNull(random, "random");
	}

	/**
	 * 후보 명단과 경품 설정은 호출 전에 확정돼 있어야 한다. 이 메서드는 DB 저장이나 실행 멱등성을 처리하지 않는다.
	 * 응모권 미사용·가중치 미적용 이벤트에서는 모든 후보의 가중치가 1이어야 한다.
	 */
	public List<DrawSelection> select(List<DrawCandidate> candidates, List<DrawPrize> prizes,
			boolean weightingEnabled) {
		Objects.requireNonNull(candidates, "candidates");
		Objects.requireNonNull(prizes, "prizes");
		if (candidates.isEmpty() || prizes.isEmpty()) {
			throw new IllegalArgumentException("candidates and prizes must not be empty");
		}

		List<DrawCandidate> remaining = new ArrayList<>(candidates);
		List<DrawPrize> orderedPrizes = new ArrayList<>(prizes);
		validateCandidates(remaining, weightingEnabled);
		validatePrizes(orderedPrizes);
		orderedPrizes.sort(Comparator.comparingInt(DrawPrize::getRank));

		BigInteger totalWeight = remaining.stream()
				.map(candidate -> BigInteger.valueOf(candidate.getWeight()))
				.reduce(BigInteger.ZERO, BigInteger::add);
		List<DrawSelection> results = new ArrayList<>();
		int selectionOrder = 0;
		for (DrawPrize prize : orderedPrizes) {
			for (int slotIndex = 0; slotIndex < prize.getWinnerCount(); slotIndex++) {
				int slot = slotIndex + 1;
				if (remaining.isEmpty()) {
					results.add(DrawSelection.unfilled(prize, slot));
					continue;
				}

				BigInteger roll = random.nextBelow(totalWeight);
				if (roll == null || roll.signum() < 0 || roll.compareTo(totalWeight) >= 0) {
					throw new IllegalStateException("random result is outside the requested range");
				}
				int selectedIndex = selectedIndex(remaining, roll);
				DrawCandidate selected = remaining.remove(selectedIndex);
			totalWeight = totalWeight.subtract(BigInteger.valueOf(selected.getWeight()));
				results.add(DrawSelection.selected(prize, slot, ++selectionOrder, selected));
			}
		}
		return List.copyOf(results);
	}

	private static void validateCandidates(List<DrawCandidate> candidates, boolean weightingEnabled) {
		Set<UUID> candidateIds = new HashSet<>();
		Set<UUID> userIds = new HashSet<>();
		for (DrawCandidate candidate : candidates) {
			Objects.requireNonNull(candidate, "candidate");
			if (!candidateIds.add(candidate.getCandidateId()) || !userIds.add(candidate.getUserId())) {
				throw new IllegalArgumentException("duplicate candidate or user");
			}
			if (!weightingEnabled && candidate.getWeight() != 1) {
				throw new IllegalArgumentException("unweighted candidates must have weight 1");
			}
		}
	}

	private static void validatePrizes(List<DrawPrize> prizes) {
		Set<UUID> prizeIds = new HashSet<>();
		Set<Integer> ranks = new HashSet<>();
		long slotCount = 0;
		for (DrawPrize prize : prizes) {
			Objects.requireNonNull(prize, "prize");
			if (!prizeIds.add(prize.getPrizeId()) || !ranks.add(prize.getRank())) {
				throw new IllegalArgumentException("duplicate prize or rank");
			}
			slotCount += prize.getWinnerCount();
			if (slotCount > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("too many prize slots");
			}
		}
	}

	private static int selectedIndex(List<DrawCandidate> candidates, BigInteger roll) {
		BigInteger boundary = BigInteger.ZERO;
		for (int index = 0; index < candidates.size(); index++) {
			boundary = boundary.add(BigInteger.valueOf(candidates.get(index).getWeight()));
			if (roll.compareTo(boundary) < 0) {
				return index;
			}
		}
		throw new IllegalStateException("candidate weights do not cover the random result");
	}
}
