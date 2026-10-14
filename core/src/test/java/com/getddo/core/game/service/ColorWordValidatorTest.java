package com.getddo.core.game.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.getddo.core.game.domain.ColorWordAnswer;
import com.getddo.core.game.domain.ColorWordColor;
import com.getddo.core.game.domain.ColorWordRoundGenerator;
import com.getddo.core.game.domain.ColorWordRules;
import com.getddo.core.game.domain.ColorWordValidationResult;
import com.getddo.core.game.domain.GamePlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ColorWordValidatorTest {

	private static final String RULES = """
			{"initialLives":3,"pointsPerCorrect":10,"initialTimeLimitMs":3000,
			 "timeLimitDecreaseMs":80,"minimumTimeLimitMs":1200}
			""";
	private final ColorWordValidator validator = new ColorWordValidator();

	private static GamePlay play(String rules, String ruleVersion, String generatorVersion, Long seed) {
		return new GamePlay(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), ruleVersion, rules,
				"a".repeat(64), generatorVersion, seed, Instant.parse("2026-10-14T00:00:00Z"), null, null, null);
	}

	private static GamePlay play() {
		return play(RULES, ColorWordRules.RULE_VERSION, ColorWordRules.GENERATOR_VERSION, 123456789L);
	}

	private static List<ColorWordAnswer> contractAnswers() {
		return List.of(new ColorWordAnswer(1, ColorWordColor.RED, 640), new ColorWordAnswer(2, null, 2920),
				new ColorWordAnswer(3, ColorWordColor.RED, 850), new ColorWordAnswer(4, null, 2920));
	}

	private static List<ColorWordAnswer> completedAfter(int correctCount) {
		ColorWordRoundGenerator generator = new ColorWordRoundGenerator(123456789);
		List<ColorWordAnswer> answers = new ArrayList<>();
		for (int index = 0; index < correctCount; index++) {
			answers.add(new ColorWordAnswer(index + 1, generator.next().getInk(), Math.max(1200, 3000 - index * 80) - 1));
		}
		for (int index = 0; index < 3; index++) {
			answers.add(new ColorWordAnswer(correctCount + index + 1, null, Math.max(1200, 3000 - correctCount * 80)));
		}
		return answers;
	}

	@Test
	@DisplayName("공유 계약의 정답·시간초과·오답·시간초과 예제는 정상 10점이다")
	void evaluatesContractExample() {
		// given / when
		ColorWordValidationResult result = validator.validate(play(), contractAnswers());
		// then
		assertThat(result.isValid()).isTrue();
		assertThat(result.getScore()).isEqualTo(10L);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 4, 5, 23, 40})
	@DisplayName("0점·40점·50점과 최소 제한 시간 이후에도 정상 종료 점수를 계산한다")
	void computesScoreIncludingZeroAndMinimumTimeLimit(int correctCount) {
		// given
		List<ColorWordAnswer> answers = completedAfter(correctCount);
		// when
		ColorWordValidationResult result = validator.validate(play(), answers);
		// then
		assertThat(result.isValid()).isTrue();
		assertThat(result.getScore()).isEqualTo(correctCount * 10L);
	}

	@Test
	@DisplayName("단어 뜻을 고른 오답 세 개도 기록이 정상이면 유효한 0점이다")
	void wrongAnswersAreNotInvalidRecords() {
		// given: 첫 세 문제의 ink는 red/yellow/yellow다.
		List<ColorWordAnswer> answers = List.of(new ColorWordAnswer(1, ColorWordColor.YELLOW, 0),
				new ColorWordAnswer(2, ColorWordColor.RED, 2999), new ColorWordAnswer(3, ColorWordColor.GREEN, 10));
		// when / then
		assertThat(validator.validate(play(), answers).getScore()).isEqualTo(0L);
	}

	@Test
	@DisplayName("첫 문제 제한 직전의 정답은 인정하고 제한에 도달한 클릭 기록은 무효다")
	void enforcesStrictClickTimeBoundary() {
		// given
		List<ColorWordAnswer> answers = new ArrayList<>(contractAnswers());
		answers.set(0, new ColorWordAnswer(1, ColorWordColor.RED, 2999));
		// when / then
		assertThat(validator.validate(play(), answers).getScore()).isEqualTo(10L);
		answers.set(0, new ColorWordAnswer(1, ColorWordColor.RED, 3000));
		assertInvalid(answers);
	}

	@Test
	@DisplayName("최소 제한 시간에서도 정확한 경계 클릭은 무효다")
	void enforcesBoundaryAtMinimumLimit() {
		// given
		List<ColorWordAnswer> answers = completedAfter(25);
		ColorWordAnswer original = answers.get(24);
		answers.set(24, new ColorWordAnswer(25, original.getAnswer(), 1200));
		// when / then
		assertInvalid(answers);
	}

	@Test
	@DisplayName("오답은 제한 시간을 줄이지 않으며 목숨이 남은 기록은 종료로 인정하지 않는다")
	void wrongAnswersDoNotReduceTimeLimit() {
		// given
		List<ColorWordAnswer> answers = List.of(new ColorWordAnswer(1, ColorWordColor.BLUE, 10),
				new ColorWordAnswer(2, null, 3000), new ColorWordAnswer(3, null, 3000));
		// when / then
		assertThat(validator.validate(play(), answers).getScore()).isEqualTo(0L);
		assertInvalid(answers.subList(0, 2));
		assertInvalid(List.of());
	}

	@ParameterizedTest
	@ValueSource(longs = {-1, 0, 2999, 3001, Long.MAX_VALUE})
	@DisplayName("시간초과 기록은 null 색과 정확한 문제 제한 시간을 가져야 한다")
	void rejectsNonCanonicalTimeout(long elapsedMs) {
		// given
		List<ColorWordAnswer> answers = completedAfter(0);
		answers.set(0, new ColorWordAnswer(1, null, elapsedMs));
		// when / then
		assertInvalid(answers);
	}

	@Test
	@DisplayName("음수 클릭 시간·중복/누락/역순 문제 번호와 종료 뒤 추가 입력은 무효다")
	void rejectsBrokenSequenceAndExtraInput() {
		// given
		List<ColorWordAnswer> answers = new ArrayList<>(contractAnswers());
		// when / then
		for (int sequence : new int[] {0, 2, 3}) {
			answers.set(1, new ColorWordAnswer(sequence, null, 2920));
			if (sequence != 2) {
				assertInvalid(answers);
			}
		}
		answers = new ArrayList<>(contractAnswers());
		answers.set(0, new ColorWordAnswer(1, ColorWordColor.RED, -1));
		assertInvalid(answers);
		answers = new ArrayList<>(contractAnswers());
		answers.add(new ColorWordAnswer(5, null, 2920));
		assertInvalid(answers);
	}

	@Test
	@DisplayName("동일 입력을 다시 판정해도 값과 입력을 바꾸거나 상태를 공유하지 않는다")
	void evaluationIsRepeatableAndDoesNotMutateInput() {
		// given
		GamePlay play = play();
		List<ColorWordAnswer> answers = contractAnswers();
		// when / then
		assertThat(validator.validate(play, answers).getScore()).isEqualTo(10L);
		assertThat(validator.validate(play, answers).getScore()).isEqualTo(10L);
		assertThat(play.isCompleted()).isFalse();
		assertThat(answers).hasSize(4);
	}

	@Test
	@DisplayName("미지원 버전·손상된 저장 규칙·잘못된 seed는 사용자 INVALID로 바꾸지 않는다")
	void propagatesServerConfigurationFailures() {
		// given / when / then
		assertThatThrownBy(() -> validator.validate(play(RULES, "future", ColorWordRules.GENERATOR_VERSION, 1L),
				contractAnswers())).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> validator.validate(play(RULES, ColorWordRules.RULE_VERSION, "future", 1L),
				contractAnswers())).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> validator.validate(play("{}", ColorWordRules.RULE_VERSION,
				ColorWordRules.GENERATOR_VERSION, 1L), contractAnswers())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> validator.validate(play(RULES, ColorWordRules.RULE_VERSION,
				ColorWordRules.GENERATOR_VERSION, 0L), contractAnswers())).isInstanceOf(IllegalArgumentException.class);
	}

	private void assertInvalid(List<ColorWordAnswer> answers) {
		ColorWordValidationResult result = validator.validate(play(), answers);
		assertThat(result.isValid()).isFalse();
		assertThat(result.getScore()).isNull();
	}
}
