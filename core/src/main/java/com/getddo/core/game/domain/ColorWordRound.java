package com.getddo.core.game.domain;

import java.util.Objects;

import lombok.Getter;

/** 글자의 뜻(word)과 실제 정답인 글자색(ink)을 구분한다. 사용자 제출은 이 값을 지정하지 않는다. */
@Getter
public final class ColorWordRound {

	private final ColorWordColor word;
	private final ColorWordColor ink;

	public ColorWordRound(ColorWordColor word, ColorWordColor ink) {
		this.word = Objects.requireNonNull(word, "word");
		this.ink = Objects.requireNonNull(ink, "ink");
	}
}
