package com.getddo.core.game.domain;

import lombok.Getter;

/**
 * 한 문제의 제출 기록이다. null 색은 시간 초과이며 속성 생략과는 다르다.
 * JSON 필수 필드·enum·숫자 형식 검사는 GD-159의 HTTP 경계가 담당한다.
 */
@Getter
public final class ColorWordAnswer {

	private final int sequence;
	private final ColorWordColor answer;
	private final long elapsedMs;

	public ColorWordAnswer(int sequence, ColorWordColor answer, long elapsedMs) {
		this.sequence = sequence;
		this.answer = answer;
		this.elapsedMs = elapsedMs;
	}
}
