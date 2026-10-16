package com.getddo.api.game.dto.request;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

import com.getddo.api.common.exception.CommonErrorCode;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.game.domain.ColorWordAnswer;
import com.getddo.core.game.domain.ColorWordColor;
import com.getddo.core.game.domain.GameResultSubmission;
import com.getddo.core.game.exception.GameErrorCode;

/** G04의 형식만 검증한다. 누락 answer와 명시적 null을 구분하며 점수 판정은 하지 않는다. */
@Builder
public record GameResultRequest(@NotBlank String playToken, @NotNull JsonNode validationData) {

	public static final int MAX_ANSWERS = 10_000;

	public GameResultSubmission toDomain(UUID gameId, UUID playId) {
		if (validationData == null || !validationData.isObject()) throw format();
		JsonNode answers = validationData.get("answers");
		if (answers == null || !answers.isArray()) throw format();
		if (answers.size() > MAX_ANSWERS) throw new BusinessException(GameErrorCode.RESULT_TOO_LARGE);
		List<ColorWordAnswer> parsed = new ArrayList<>(answers.size());
		for (JsonNode answer : answers) {
			if (!answer.isObject()) throw format();
			JsonNode sequence = answer.get("sequence");
			JsonNode elapsed = answer.get("elapsedMs");
			JsonNode color = answer.get("answer");
			if (sequence == null || !sequence.isIntegralNumber() || !sequence.canConvertToInt()
					|| elapsed == null || !elapsed.isIntegralNumber() || !elapsed.canConvertToLong() || color == null) {
				throw format();
			}
			ColorWordColor choice = null;
			if (!color.isNull()) {
				if (!color.isString()) throw format();
				choice = switch (color.asString()) {
					case "red" -> ColorWordColor.RED;
					case "blue" -> ColorWordColor.BLUE;
					case "yellow" -> ColorWordColor.YELLOW;
					case "green" -> ColorWordColor.GREEN;
					default -> throw format();
				};
			}
			parsed.add(new ColorWordAnswer(sequence.intValue(), choice, elapsed.longValue()));
		}
		return new GameResultSubmission(gameId, playId, playToken, parsed);
	}

	private static BusinessException format() {
		return new BusinessException(CommonErrorCode.INVALID_FORMAT);
	}
}
