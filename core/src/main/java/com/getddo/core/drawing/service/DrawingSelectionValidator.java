package com.getddo.core.drawing.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawCandidate;
import com.getddo.core.drawing.domain.DrawPrize;

/** 후보·가중치·경품 설정이 추첨에 사용할 수 있는 값인지 검사한다. */
final class DrawingSelectionValidator {

	/** 검증 함수만 제공하며 인스턴스 생성을 막는다. */
	private DrawingSelectionValidator() {
	}

	/** 후보 ID·사용자 중복과 미가중치 조건을 검증하여 이벤트 내 중복 당첨을 방지한다. */
	static void validateCandidates(List<DrawCandidate> candidates, boolean weightingEnabled) {
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

	/** 경품·등수 중복과 전체 자리 수의 정수 범위를 검증한다. */
	static void validatePrizes(List<DrawPrize> prizes) {
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
}
