package com.getddo.core.game.domain;

import java.util.Objects;
import java.util.function.DoubleSupplier;

/**
 * 발급 시 보존한 unsigned 32-bit seed로 문제를 순차 재현한다. 매 플레이마다 새 인스턴스를 만든다.
 * 공개 seed의 재현용 생성기이며 사람의 플레이를 증명하는 암호학적 검증은 아니다.
 */
public final class ColorWordRoundGenerator {

	private static final ColorWordColor[] COLORS = ColorWordColor.values();
	private final DoubleSupplier random;
	private ColorWordRound previous;

	public ColorWordRoundGenerator(long seed) {
		if (seed < 1 || seed > 0xffff_ffffL) {
			throw new IllegalArgumentException("0이 아닌 unsigned 32-bit seed가 필요합니다.");
		}
		// int의 overflow와 >>>를 사용해 JavaScript의 32-bit 비트 연산을 그대로 재현한다.
		this.random = new DoubleSupplier() {
			private int state = (int) seed;

			@Override
			public double getAsDouble() {
				state ^= state << 13;
				state ^= state >>> 17;
				state ^= state << 5;
				return Integer.toUnsignedLong(state) / 4294967296.0;
			}
		};
	}

	/** 반복 추출의 최종 대체 경로를 고정 난수로 검증하기 위한 패키지 내부 생성자다. */
	ColorWordRoundGenerator(DoubleSupplier random) {
		this.random = Objects.requireNonNull(random, "random");
	}

	/** FE의 단어→불일치 여부→다른 색 추출 순서와 최대 10회 재추출을 유지한다. */
	public ColorWordRound next() {
		for (int attempt = 0; attempt < 10; attempt++) {
			ColorWordColor word = COLORS[(int) (random.getAsDouble() * COLORS.length)];
			ColorWordColor ink = word;
			if (random.getAsDouble() < 0.75) {
				int selected = (int) (random.getAsDouble() * (COLORS.length - 1));
				for (ColorWordColor candidate : COLORS) {
					if (candidate != word && selected-- == 0) {
						ink = candidate;
						break;
					}
				}
			}
			if (previous == null || previous.getWord() != word || previous.getInk() != ink) {
				previous = new ColorWordRound(word, ink);
				return previous;
			}
		}
		// FE도 추가 난수 소비 없이 색 순서의 첫 대체색을 선택한다.
		for (ColorWordColor candidate : COLORS) {
			if (candidate != previous.getInk()) {
				previous = new ColorWordRound(previous.getWord(), candidate);
				return previous;
			}
		}
		throw new IllegalStateException("문제 생성 색 구성이 올바르지 않습니다.");
	}
}
