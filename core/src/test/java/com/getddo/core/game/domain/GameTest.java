package com.getddo.core.game.domain;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameTest {

	@Test
	@DisplayName("신규 생성과 저장 모델 복원 모두 공백과 컬럼 길이 초과를 거절한다")
	void rejectsBlankAndOverlongFields() {
		// given
		List<String[]> invalid = List.of(
				new String[] {"", "게임", "v1"},
				new String[] {" \t\n", "게임", "v1"},
				new String[] {"x".repeat(51), "게임", "v1"},
				new String[] {"COLOR_WORD", "", "v1"},
				new String[] {"COLOR_WORD", " \t\n", "v1"},
				new String[] {"COLOR_WORD", "가".repeat(101), "v1"},
				new String[] {"COLOR_WORD", "게임", ""},
				new String[] {"COLOR_WORD", "게임", " \t\n"},
				new String[] {"COLOR_WORD", "게임", "v".repeat(31)});

		// when / then
		for (String[] input : invalid) {
			assertThatThrownBy(() -> Game.create(input[0], input[1], null, "{}", input[2]))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> new Game(UUID.randomUUID(), input[0], input[1], null, "{}", input[2], false))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	@DisplayName("컬럼 최대 길이의 값은 변경 없이 보존하고 신규 상태를 유지한다")
	void acceptsMaximumLengths() {
		// given
		String code = "c".repeat(50);
		String name = "가".repeat(100);
		String version = "v".repeat(30);

		// when
		Game game = Game.create(code, name, null, "{}", version);

		// then
		assertThat(game.getId()).isNull();
		assertThat(game.isActive()).isTrue();
		assertThat(game.getDescription()).isNull();
		assertThat(game.getCode()).isEqualTo(code);
		assertThat(game.getName()).isEqualTo(name);
		assertThat(game.getRuleVersion()).isEqualTo(version);
	}

	@Test
	@DisplayName("utf8mb4 컬럼처럼 보조 평면 문자도 한 문자로 계산한다")
	void countsUnicodeCharacters() {
		// given
		String name = "😀".repeat(100);

		// when
		Game game = Game.create("COLOR_WORD", name, null, "{}", "v1");

		// then
		assertThat(game.getName()).isEqualTo(name);
		assertThatThrownBy(() -> Game.create("COLOR_WORD", name + "😀", null, "{}", "v1"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("기존 필수 필드의 null 거절을 유지한다")
	void rejectsNullRequiredFields() {
		// given
		List<Runnable> invalid = List.of(
				() -> Game.create(null, "게임", null, "{}", "v1"),
				() -> Game.create("COLOR_WORD", null, null, "{}", "v1"),
				() -> Game.create("COLOR_WORD", "게임", null, null, "v1"),
				() -> Game.create("COLOR_WORD", "게임", null, "{}", null));

		// when / then
		for (Runnable input : invalid) {
			assertThatThrownBy(input::run).isInstanceOf(NullPointerException.class);
		}
	}
}
