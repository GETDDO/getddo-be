package com.getddo.api.game.controller;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.game.domain.ColorWordRules;
import com.getddo.core.game.domain.Game;
import com.getddo.core.game.domain.GameDetailResult;
import com.getddo.core.game.exception.GameErrorCode;
import com.getddo.core.game.service.GameQueryService;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GameControllerTest {

	private static final UUID USER = UUID.randomUUID();
	private static final UUID GAME = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-10-15T04:30:00.123456Z");
	private static final String RULES = """
			{"initialLives":3,"pointsPerCorrect":10,"initialTimeLimitMs":3000,
			 "timeLimitDecreaseMs":80,"minimumTimeLimitMs":1200,"internalNote":"private"}
			""";
	private final GameQueryService queries = mock(GameQueryService.class);
	private User selected;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		selected = user(UserRole.USER);
		mvc = MockMvcBuilders.standaloneSetup(new GameController(queries,
				new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC))))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(new UserService(
						id -> Optional.of(selected).filter(value -> value.getId().equals(id)))))
				.build();
	}

	@ParameterizedTest
	@EnumSource(UserRole.class)
	@DisplayName("USER와 ADMIN은 선택 멤버십 없이 목록과 공개 상세를 조회한다")
	void returnsPublicDataForBothRoles(UserRole role) throws Exception {
		// given
		selected = user(role);
		Game game = new Game(GAME, "COLOR_WORD", "색깔 게임", null, RULES, "color-word-v1", true);
		when(queries.findGames()).thenReturn(List.of(game));
		when(queries.findGame(GAME)).thenReturn(new GameDetailResult(game, ColorWordRules.fromSnapshot(RULES)));
		// when / then
		mvc.perform(headers(get("/api/v1/games"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0]", aMapWithSize(4)))
				.andExpect(jsonPath("$.data[0].id").value(GAME.toString()))
				.andExpect(jsonPath("$.data[0].description").value(nullValue()))
				.andExpect(jsonPath("$.data[0].rules").doesNotExist());
		mvc.perform(headers(get("/api/v1/games/{gameId}", GAME))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data", aMapWithSize(7)))
				.andExpect(jsonPath("$.data.ruleVersion").value("color-word-v1"))
				.andExpect(jsonPath("$.data.description").value(nullValue()))
				.andExpect(jsonPath("$.data.serverTime").value(NOW.toString()))
				.andExpect(jsonPath("$.data.rules", aMapWithSize(5)))
				.andExpect(jsonPath("$.data.rules.initialLives").value(3))
				.andExpect(jsonPath("$.data.rules.internalNote").doesNotExist())
				.andExpect(jsonPath("$.data.playToken").doesNotExist());
	}

	@Test
	@DisplayName("조회할 게임이 없으면 성공 봉투의 빈 배열을 반환한다")
	void returnsEmptyArray() throws Exception {
		// given
		when(queries.findGames()).thenReturn(List.of());
		// when / then
		mvc.perform(headers(get("/api/v1/games"))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(0)));
	}

	@Test
	@DisplayName("사용자 문맥 누락·형식·역할·멤버십 불일치와 잘못된 게임 UUID는 조회 전에 거절한다")
	void validatesHeadersAndUuid() throws Exception {
		// given / when / then
		mvc.perform(get("/api/v1/games")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/games").header("X-User-ID", "invalid").header("X-User-Role", "USER"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/games").header("X-User-ID", USER).header("X-User-Role", "ADMIN"))
				.andExpect(status().isForbidden());
		mvc.perform(headers(get("/api/v1/games")).header("X-User-Membership", "vip"))
				.andExpect(status().isConflict());
		mvc.perform(headers(get("/api/v1/games/{gameId}", "invalid"))).andExpect(status().isBadRequest());
		verifyNoInteractions(queries);
	}

	@ParameterizedTest
	@EnumSource(value = GameErrorCode.class, names = {"GAME_NOT_FOUND", "GAME_DETAIL_NOT_AVAILABLE"})
	@DisplayName("조회 업무 오류는 공통 예외 처리기로 상태와 코드를 보존한다")
	void preservesErrors(GameErrorCode error) throws Exception {
		// given
		when(queries.findGame(GAME)).thenThrow(new BusinessException(error));
		// when / then
		mvc.perform(headers(get("/api/v1/games/{gameId}", GAME))).andExpect(status().is(error.getStatus()))
				.andExpect(jsonPath("$.code").value(error.getCode()))
				.andExpect(jsonPath("$.data").value(nullValue()));
	}

	@Test
	@DisplayName("저장 설정 장애는 내부 메시지를 노출하지 않고 공통 500을 반환한다")
	void hidesInternalFailure() throws Exception {
		// given
		when(queries.findGame(GAME)).thenThrow(new IllegalStateException("private-json"));
		// when / then
		mvc.perform(headers(get("/api/v1/games/{gameId}", GAME))).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("COMMON-001"))
				.andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
	}

	private MockHttpServletRequestBuilder headers(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", USER).header("X-User-Role", selected.getRole().name());
	}

	private static User user(UserRole role) {
		return new User(USER, "사용자", role, UserStatus.ACTIVE, null, null, null, null, null, null, null);
	}
}
