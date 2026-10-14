package com.getddo.core.game.domain;

import lombok.Getter;

/** 기록 규칙 위반은 null 점수로 구분한다. 정상적인 오답만으로 끝난 0점은 유효하다. */
@Getter
public final class ColorWordValidationResult {

	private final Long score;

	private ColorWordValidationResult(Long score) {
		this.score = score;
	}

	public static ColorWordValidationResult valid(long score) {
		if (score < 0) {
			throw new IllegalArgumentException("유효 점수는 음수일 수 없습니다.");
		}
		return new ColorWordValidationResult(score);
	}

	public static ColorWordValidationResult invalid() {
		return new ColorWordValidationResult(null);
	}

	public boolean isValid() {
		return score != null;
	}
}
