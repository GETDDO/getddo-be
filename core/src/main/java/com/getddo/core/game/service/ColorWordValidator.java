package com.getddo.core.game.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.getddo.core.game.domain.ColorWordAnswer;
import com.getddo.core.game.domain.ColorWordRound;
import com.getddo.core.game.domain.ColorWordRoundGenerator;
import com.getddo.core.game.domain.ColorWordRules;
import com.getddo.core.game.domain.ColorWordValidationResult;
import com.getddo.core.game.domain.GamePlay;

/**
 * 저장된 시작 스냅샷으로 색깔 게임의 자연 종료 기록과 서버 점수를 판정한다. DB 조회·완료 저장·지급은 하지 않는다.
 * 소유권·토큰·동일 결과 재전송 검증은 호출자(GD-159)가 판정 전에 담당한다.
 */
@Service
public class ColorWordValidator {

	/**
	 * 규칙을 어긴 기록은 무효이며 정상 오답·시간 초과는 목숨만 줄인다.
	 * 지원하지 않거나 손상된 서버 스냅샷은 예외를 전파하고 사용자 기록의 INVALID로 바꾸지 않는다.
	 *
	 * @param play 발급 당시 규칙·생성 버전·seed를 보존한 플레이
	 * @param answers HTTP 형식 검사를 거친 기록. 순서를 바꾸거나 시간 초과를 보정하지 않는다
	 */
	public ColorWordValidationResult validate(GamePlay play, List<ColorWordAnswer> answers) {
		Objects.requireNonNull(play, "play");
		Objects.requireNonNull(answers, "answers");
		if (!play.hasVerificationContext() || !ColorWordRules.RULE_VERSION.equals(play.getRuleVersion())
				|| !ColorWordRules.GENERATOR_VERSION.equals(play.getGeneratorVersion())) {
			throw new IllegalStateException("지원하는 색깔 게임의 시작 스냅샷이 필요합니다.");
		}
		ColorWordRules rules = ColorWordRules.fromSnapshot(play.getRulesSnapshot());
		ColorWordRoundGenerator generator = new ColorWordRoundGenerator(play.getGenerationSeed());
		long correctCount = 0;
		int lives = rules.getInitialLives();
		for (int index = 0; index < answers.size(); index++) {
			ColorWordAnswer answer = Objects.requireNonNull(answers.get(index), "answer");
			if (lives == 0 || answer.getSequence() != index + 1 || answer.getElapsedMs() < 0) {
				return ColorWordValidationResult.invalid();
			}
			long limit = Math.max(rules.getMinimumTimeLimitMs(),
					rules.getInitialTimeLimitMs() - correctCount * rules.getTimeLimitDecreaseMs());
			// 시간 초과는 null 색과 정확한 제한 시간으로 정규화된 기록만 인정한다.
			if (answer.getAnswer() == null ? answer.getElapsedMs() != limit : answer.getElapsedMs() >= limit) {
				return ColorWordValidationResult.invalid();
			}
			ColorWordRound round = generator.next();
			if (answer.getAnswer() == round.getInk()) {
				correctCount++;
			} else {
				lives--;
			}
		}
		return lives == 0 ? ColorWordValidationResult.valid(correctCount * rules.getPointsPerCorrect())
				: ColorWordValidationResult.invalid();
	}
}
