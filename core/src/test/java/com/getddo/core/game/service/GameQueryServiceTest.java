package com.getddo.core.game.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.game.domain.ColorWordRules;
import com.getddo.core.game.domain.Game;
import com.getddo.core.game.exception.GameErrorCode;
import com.getddo.core.game.repository.GameRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GameQueryServiceTest {

	private static final UUID ID = UUID.randomUUID();
	private static final String RULES = """
			{"initialLives":3,"pointsPerCorrect":10,"initialTimeLimitMs":3000,
			 "timeLimitDecreaseMs":80,"minimumTimeLimitMs":1200,"internalNote":"private"}
			""";
	private final GameRepository games = mock(GameRepository.class);
	private final GamePlayStartHandler handler = new ColorWordPlayStartHandler();
	private final GameQueryService service = new GameQueryService(games, List.of(handler));

	@Test
	@DisplayName("활성 목록은 빈 배열을 허용하고 시작 미지원 게임도 숨기지 않는다")
	void listsAllActiveGamesAndEmptyList() {
		// given
		Game color = game("COLOR_WORD", "color-word-v1", true, RULES);
		Game takko = game("TAKKORUN", "v1", true, "{}");
		when(games.findActive()).thenReturn(List.of(), List.of(color, takko));
		// when / then
		assertThat(service.findGames()).isEmpty();
		assertThat(service.findGames()).containsExactly(color, takko);
	}

	@Test
	@DisplayName("활성 색깔 게임은 저장된 메타데이터와 검증한 공개 규칙을 반환한다")
	void readsStoredRules() {
		// given
		Game game = game("COLOR_WORD", "color-word-v1", true, RULES);
		when(games.findById(ID)).thenReturn(Optional.of(game));
		// when
		var result = service.findGame(ID);
		// then
		assertThat(result.getGame()).isSameAs(game);
		assertThat(result.getRules()).isInstanceOfSatisfying(ColorWordRules.class,
				rules -> assertThat(rules.getInitialTimeLimitMs()).isEqualTo(3000));
	}

	@Test
	@DisplayName("없는 게임과 비활성 게임은 규칙 준비 전에 같은 GAME-002로 숨긴다")
	void hidesMissingAndInactiveGames() {
		// given
		GamePlayStartHandler unused = mock(GamePlayStartHandler.class);
		GameQueryService queries = new GameQueryService(games, List.of(unused));
		when(games.findById(ID)).thenReturn(Optional.empty(),
				Optional.of(game("COLOR_WORD", "color-word-v1", false, "{}")));
		// when / then
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> queries.findGame(ID)).isInstanceOfSatisfying(BusinessException.class,
					error -> assertThat(error.getErrorCode()).isEqualTo(GameErrorCode.GAME_NOT_FOUND));
		}
		verifyNoInteractions(unused);
	}

	@Test
	@DisplayName("타꼬런과 미지원 색깔 규칙 버전의 상세는 GAME-004로 거절한다")
	void rejectsUnsupportedDetail() {
		// given / when / then
		for (Game game : List.of(game("TAKKORUN", "v1", true, "{}"),
				game("COLOR_WORD", "color-word-v2", true, RULES))) {
			when(games.findById(ID)).thenReturn(Optional.of(game));
			assertThatThrownBy(() -> service.findGame(ID)).isInstanceOfSatisfying(BusinessException.class,
					error -> assertThat(error.getErrorCode()).isEqualTo(GameErrorCode.GAME_DETAIL_NOT_AVAILABLE));
		}
	}

	@Test
	@DisplayName("지원 버전의 손상된 저장 규칙은 사용자 오류나 기본값으로 숨기지 않는다")
	void preservesServerFailureForCorruptRules() {
		// given / when / then
		for (String rules : List.of("{}", "{private-json", RULES.replace("3000", "2999"))) {
			when(games.findById(ID)).thenReturn(Optional.of(game("COLOR_WORD", "color-word-v1", true, rules)));
			assertThatThrownBy(() -> service.findGame(ID)).isInstanceOf(IllegalStateException.class)
					.hasMessage("저장된 색깔 게임 규칙이 올바르지 않습니다.");
		}
	}

	@Test
	@DisplayName("공개 규칙 처리기가 중복되면 첫 처리기를 임의 선택하지 않는다")
	void rejectsDuplicateHandlers() {
		// given
		when(games.findById(ID)).thenReturn(Optional.of(game("COLOR_WORD", "color-word-v1", true, RULES)));
		GameQueryService duplicate = new GameQueryService(games, List.of(handler, handler));
		// when / then
		assertThatThrownBy(() -> duplicate.findGame(ID)).isInstanceOf(IllegalStateException.class)
				.hasMessage("게임 상세 규칙 처리기가 중복 등록되었습니다.");
	}

	private static Game game(String code, String version, boolean active, String rules) {
		return new Game(ID, code, "게임", null, rules, version, active);
	}
}
