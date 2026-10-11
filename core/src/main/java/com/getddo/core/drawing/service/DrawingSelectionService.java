package com.getddo.core.drawing.service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.getddo.core.drawing.domain.DrawCandidate;
import com.getddo.core.drawing.domain.DrawPrize;
import com.getddo.core.drawing.domain.DrawSelection;

/** 확정된 후보·경품만 입력받아 상위 등수부터 당첨자를 선정한다. */
@Service
public final class DrawingSelectionService {

	private final DrawRandom random;

	/** 운영 Bean은 보안 난수 구현으로 생성한다. 테스트는 난수 주입 생성자로 선정 순서를 제어할 수 있다. */
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
		DrawingSelectionValidator.validateCandidates(remaining, weightingEnabled);
		DrawingSelectionValidator.validatePrizes(orderedPrizes);
		orderedPrizes.sort(Comparator.comparingInt(DrawPrize::getRank));

		BigInteger totalWeight = BigInteger.ZERO;
		for (DrawCandidate candidate : remaining) {
			totalWeight = totalWeight.add(BigInteger.valueOf(candidate.getWeight()));
		}
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

	/** 누적 가중치 구간에 해당하는 후보 위치를 찾는다. 난수는 전체 가중치 미만이어야 한다. */
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
