package com.getddo.core.game.domain;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColorWordRulesTest {

	private static final String RULES = """
			{"initialLives":3,"pointsPerCorrect":10,"initialTimeLimitMs":3000,
			 "timeLimitDecreaseMs":80,"minimumTimeLimitMs":1200}
			""";

	@Test
	@DisplayName("저장된 스냅샷의 공개 규칙을 읽으며 내부 필드는 공개 규칙 모델에 포함하지 않는다")
	void readsVersionedPublicRules() {
		// given
		String snapshot = RULES.replace("1200}", "1200,\"internalNote\":\"private\"}");
		// when
		ColorWordRules rules = ColorWordRules.fromSnapshot(snapshot);
		// then
		assertThat(rules.getInitialLives()).isEqualTo(3);
		assertThat(rules.getPointsPerCorrect()).isEqualTo(10);
		assertThat(rules.getInitialTimeLimitMs()).isEqualTo(3000);
		assertThat(rules.getTimeLimitDecreaseMs()).isEqualTo(80);
		assertThat(rules.getMinimumTimeLimitMs()).isEqualTo(1200);
	}

	@Test
	@DisplayName("누락·형식·버전 규칙 불일치는 기본값으로 보정하지 않는다")
	void rejectsInvalidStoredRules() {
		// given
		List<String> invalid = List.of("", "null", "[]", "{}", "{broken",
				RULES.replace("\"initialLives\":3,", ""),
				RULES.replace("\"initialLives\":3", "\"initialLives\":\"3\""),
				RULES.replace("\"initialLives\":3", "\"initialLives\":3.0"),
				RULES.replace("\"initialLives\":3", "\"initialLives\":2147483648"),
				RULES.replace("\"initialLives\":3", "\"initialLives\":null"),
				RULES.replace("\"initialLives\":3", "\"initialLives\":4"),
				RULES.replace("\"pointsPerCorrect\":10", "\"pointsPerCorrect\":11"),
				RULES.replace("\"initialTimeLimitMs\":3000", "\"initialTimeLimitMs\":2999"),
				RULES.replace("\"timeLimitDecreaseMs\":80", "\"timeLimitDecreaseMs\":81"),
				RULES.replace("\"minimumTimeLimitMs\":1200", "\"minimumTimeLimitMs\":1199"),
				RULES.replace("\"initialLives\":3", "\"initialLives\":3,\"initialLives\":3"),
				RULES + "{}");
		// when / then
		for (String snapshot : invalid) {
			assertThatThrownBy(() -> ColorWordRules.fromSnapshot(snapshot)).isInstanceOf(RuntimeException.class);
		}
	}
}
