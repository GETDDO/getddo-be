package com.getddo.core.game.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.game.domain.ColorWordRules;
import com.getddo.core.game.domain.Game;
import com.getddo.core.game.domain.GamePlay;
import com.getddo.core.game.domain.GamePlayStartResult;
import com.getddo.core.game.domain.GameStartConfiguration;
import com.getddo.core.game.domain.GameStartRules;
import com.getddo.core.game.exception.GameErrorCode;
import com.getddo.core.game.repository.GamePlayRepository;
import com.getddo.core.game.repository.GameRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GamePlayStartServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID GAME_ID = UUID.randomUUID();
	private static final UUID PLAY_ID = UUID.randomUUID();
	private static final Instant START = Instant.parse("2026-10-14T01:00:00.123456Z");
	private static final String RULES = """
			{"initialLives":3,"pointsPerCorrect":10,"initialTimeLimitMs":3000,
			 "timeLimitDecreaseMs":80,"minimumTimeLimitMs":1200,"internalNote":"private"}
			""";
	private final GameRepository games = mock(GameRepository.class);
	private final GamePlayRepository plays = mock(GamePlayRepository.class);
	private final GamePlayStartService service = new GamePlayStartService(games, plays, List.of(new ColorWordPlayStartHandler()));

	@ParameterizedTest
	@EnumSource(UserRole.class)
	@DisplayName("활성 일반 사용자·관리자에게 새 플레이를 발급하고 원문 토큰 대신 해시와 규칙을 저장한다")
	void startsForBothActiveRoles(UserRole role) throws Exception {
		// given
		User user = user(role, UserStatus.ACTIVE);
		when(games.findById(GAME_ID)).thenReturn(Optional.of(game("COLOR_WORD", "color-word-v1", true, RULES)));
		when(plays.insert(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));
		// when
		GamePlayStartResult result = service.start(user, GAME_ID);
		// then
		ArgumentCaptor<GamePlay> input = ArgumentCaptor.forClass(GamePlay.class);
		verify(plays).insert(input.capture());
		GamePlay pending = input.getValue();
		assertThat(pending.getId()).isNull();
		assertThat(pending.getStartedAt()).isNull();
		assertThat(pending.getGameId()).isEqualTo(GAME_ID);
		assertThat(pending.getUserId()).isEqualTo(USER_ID);
		assertThat(pending.getRulesSnapshot()).isEqualTo(RULES);
		assertThat(pending.getRuleVersion()).isEqualTo("color-word-v1");
		assertThat(pending.getGeneratorVersion()).isEqualTo("color-word-generator-v1");
		assertThat(pending.getGenerationSeed()).isBetween(1L, 4294967295L);
		assertThat(pending.getScore()).isNull();
		assertThat(pending.getResultHash()).isNull();
		assertThat(pending.getCompletedAt()).isNull();
		String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(result.getPlayToken().getBytes(StandardCharsets.UTF_8)));
		assertThat(pending.getPlayTokenHash()).isEqualTo(expectedHash).isNotEqualTo(result.getPlayToken());
		assertThat(result.getPlayToken()).matches("[A-Za-z0-9_-]{43}");
		assertThat(result.getPlay().getId()).isEqualTo(PLAY_ID);
		assertThat(result.getPlay().getStartedAt()).isEqualTo(START);
		assertThat(result.getRules()).isInstanceOfSatisfying(ColorWordRules.class,
				rules -> assertThat(rules.getInitialLives()).isEqualTo(3));
	}

	@ParameterizedTest
	@EnumSource(UserRole.class)
	@DisplayName("비활성 일반 사용자·관리자는 게임 조회와 플레이 저장 전에 거절한다")
	void rejectsInactiveUsersBeforeLookup(UserRole role) {
		// given / when / then
		assertThatThrownBy(() -> service.start(user(role, UserStatus.INACTIVE), GAME_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.getErrorCode()).isEqualTo(GameErrorCode.USER_INACTIVE));
		verifyNoInteractions(games, plays);
	}

	@Test
	@DisplayName("없는 게임이면 게임 부재 오류를 반환하며 플레이를 만들지 않는다")
	void rejectsMissingGame() {
		// given
		when(games.findById(GAME_ID)).thenReturn(Optional.empty());
		// when / then
		assertThatThrownBy(() -> service.start(user(UserRole.USER, UserStatus.ACTIVE), GAME_ID))
				.isInstanceOfSatisfying(BusinessException.class,
						error -> assertThat(error.getErrorCode()).isEqualTo(GameErrorCode.GAME_NOT_FOUND));
		verifyNoInteractions(plays);
	}

	@Test
	@DisplayName("비활성·미지원 게임·미지원 규칙 버전은 시작할 수 없다")
	void rejectsUnavailableGames() {
		// given
		Game[] unavailable = {game("COLOR_WORD", "color-word-v1", false, RULES),
				game("TAKKORUN", "v1", true, RULES), game("COLOR_WORD", "color-word-v2", true, RULES)};
		// when / then
		for (Game game : unavailable) {
			when(games.findById(GAME_ID)).thenReturn(Optional.of(game));
			assertThatThrownBy(() -> service.start(user(UserRole.USER, UserStatus.ACTIVE), GAME_ID))
					.isInstanceOfSatisfying(BusinessException.class,
							error -> assertThat(error.getErrorCode()).isEqualTo(GameErrorCode.GAME_NOT_AVAILABLE));
		}
		verifyNoInteractions(plays);
	}

	@Test
	@DisplayName("저장 규칙 오류는 사용자 입력 오류로 바꾸거나 기본 규칙으로 덮지 않고 플레이를 만들지 않는다")
	void rejectsInvalidConfigurationWithoutExposingJson() {
		// given / when / then
		for (String invalid : new String[] {"{}", "{broken-private", RULES.replace("3000", "2999")}) {
			when(games.findById(GAME_ID)).thenReturn(Optional.of(game("COLOR_WORD", "color-word-v1", true, invalid)));
			assertThatThrownBy(() -> service.start(user(UserRole.USER, UserStatus.ACTIVE), GAME_ID))
					.isInstanceOf(IllegalStateException.class)
					.hasMessage("저장된 색깔 게임 규칙이 올바르지 않습니다.")
					.satisfies(error -> {
						assertThat(error.getCause()).isInstanceOf(IllegalStateException.class).hasNoCause();
						assertThat(error.getCause().getMessage())
								.matches("(?:java\\.lang\\.IllegalArgumentException|tools\\.jackson\\.[A-Za-z0-9_.$]+)");
						assertThat(error.getCause().getStackTrace()).isNotEmpty();
						java.io.StringWriter trace = new java.io.StringWriter();
						error.printStackTrace(new java.io.PrintWriter(trace));
						assertThat(trace.toString()).doesNotContain(invalid, "broken-private", "2999");
					});
		}
		verifyNoInteractions(plays);
	}

	@Test
	@DisplayName("저장 실패는 성공 응답으로 바꾸지 않고 호출자에게 전파한다")
	void propagatesPersistenceFailure() {
		// given
		when(games.findById(GAME_ID)).thenReturn(Optional.of(game("COLOR_WORD", "color-word-v1", true, RULES)));
		IllegalStateException failure = new IllegalStateException("test persistence failure");
		when(plays.insert(any())).thenThrow(failure);
		// when / then
		assertThatThrownBy(() -> service.start(user(UserRole.USER, UserStatus.ACTIVE), GAME_ID)).isSameAs(failure);
	}

	@Test
	@DisplayName("다른 게임 처리기를 추가하면 같은 시작 서비스로 해당 규칙과 생성 버전을 저장한다")
	void usesRegisteredGameHandler() {
		// given: 타꼬런 계약을 가정하지 않는 테스트용 게임이다.
		GameStartRules rules = mock(GameStartRules.class);
		GamePlayStartHandler handler = mock(GamePlayStartHandler.class);
		Game game = game("TEST_GAME", "test-v1", true, "{}");
		when(games.findById(GAME_ID)).thenReturn(Optional.of(game));
		when(handler.supports(game)).thenReturn(true);
		when(handler.prepare(game)).thenReturn(new GameStartConfiguration(rules, "test-generator-v1"));
		when(plays.insert(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));
		GamePlayStartService extended = new GamePlayStartService(games, plays,
				List.of(new ColorWordPlayStartHandler(), handler));
		// when
		GamePlayStartResult result = extended.start(user(UserRole.USER, UserStatus.ACTIVE), GAME_ID);
		// then
		assertThat(result.getRules()).isSameAs(rules);
		assertThat(result.getPlay().getGeneratorVersion()).isEqualTo("test-generator-v1");
		assertThat(result.getPlay().getGameId()).isEqualTo(GAME_ID);
		verify(plays).insert(any());
	}

	@Test
	@DisplayName("같은 게임을 지원하는 처리기가 중복되면 임의 선택하거나 저장하지 않는다")
	void rejectsDuplicateHandlers() {
		// given
		when(games.findById(GAME_ID)).thenReturn(Optional.of(game("COLOR_WORD", "color-word-v1", true, RULES)));
		GamePlayStartService duplicated = new GamePlayStartService(games, plays,
				List.of(new ColorWordPlayStartHandler(), new ColorWordPlayStartHandler()));
		// when / then
		assertThatThrownBy(() -> duplicated.start(user(UserRole.USER, UserStatus.ACTIVE), GAME_ID))
				.isInstanceOf(IllegalStateException.class).hasMessage("게임 시작 처리기가 중복 등록되었습니다.");
		verifyNoInteractions(plays);
	}

	private static User user(UserRole role, UserStatus status) {
		return new User(USER_ID, "사용자", role, status, null, null, null, null, null, null, null);
	}

	private static Game game(String code, String version, boolean active, String rules) {
		return new Game(GAME_ID, code, "게임", null, rules, version, active);
	}

	private static GamePlay saved(GamePlay pending) {
		return new GamePlay(PLAY_ID, pending.getGameId(), pending.getUserId(), pending.getRuleVersion(),
				pending.getRulesSnapshot(), pending.getPlayTokenHash(), pending.getGeneratorVersion(),
				pending.getGenerationSeed(), START, null, null, null);
	}
}
