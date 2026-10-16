package com.getddo.api.game.dto.request;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.game.domain.GameResultSubmission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameResultRequestTest {
	private final JsonMapper json = JsonMapper.builder().build();
	private final UUID game = UUID.randomUUID();
	private final UUID play = UUID.randomUUID();

	@Test
	@DisplayName("JSON 키 순서와 공백이 달라도 같은 입력이며 색·시간·배열 순서가 바뀌면 다른 입력이다")
	void hashesNormalizedValues() {
		// given / when
		GameResultSubmission first = parse("[{\"sequence\":1,\"answer\":null,\"elapsedMs\":3000}]");
		GameResultSubmission reordered = parse("[ {\"elapsedMs\":3000, \"answer\":null, \"sequence\":1} ]");
		GameResultSubmission changed = parse("[{\"sequence\":1,\"answer\":\"red\",\"elapsedMs\":3000}]");
		// then
		assertThat(first.getResultHash()).isEqualTo(reordered.getResultHash()).isNotEqualTo(changed.getResultHash());
		assertThat(first.getAnswers().getFirst().getAnswer()).isNull();
		assertThatThrownBy(() -> first.getAnswers().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"[{\"sequence\":1,\"elapsedMs\":3000}]",
			"[{\"sequence\":1.5,\"answer\":null,\"elapsedMs\":3000}]",
			"[{\"sequence\":2147483648,\"answer\":null,\"elapsedMs\":3000}]",
			"[{\"sequence\":1,\"answer\":null,\"elapsedMs\":\"3000\"}]",
			"[{\"sequence\":1,\"answer\":null,\"elapsedMs\":9223372036854775808}]",
			"[{\"sequence\":1,\"answer\":\"RED\",\"elapsedMs\":1}]", "[null]"
	})
	@DisplayName("누락된 답변과 잘못된 enum·정수 형식을 COMMON-005로 거절한다")
	void rejectsMalformedAnswers(String answers) {
		// given / when / then
		assertThatThrownBy(() -> parse(answers)).isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode().getCode()).isEqualTo("COMMON-005"));
	}

	private GameResultSubmission parse(String answers) {
		return new GameResultRequest("token", json.readTree("{\"answers\":" + answers + "}"))
				.toDomain(game, play);
	}
}
