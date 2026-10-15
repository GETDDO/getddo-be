package com.getddo.api.game.controller;

import java.time.Instant;
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
import com.getddo.core.game.domain.ColorWordRules;
import com.getddo.core.game.domain.GamePlay;
import com.getddo.core.game.domain.GamePlayStartResult;
import com.getddo.core.game.exception.GameErrorCode;
import com.getddo.core.game.service.GamePlayStartService;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GamePlayControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID GAME_ID = UUID.randomUUID();
	private static final UUID PLAY_ID = UUID.randomUUID();
	private static final String RULES = """
			{"initialLives":3,"pointsPerCorrect":10,"initialTimeLimitMs":3000,
			 "timeLimitDecreaseMs":80,"minimumTimeLimitMs":1200}
			""";
	private final GamePlayStartService service = mock(GamePlayStartService.class);
	private User selectedUser;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		selectedUser = user(UserRole.USER);
		mvc = MockMvcBuilders.standaloneSetup(new GamePlayController(service))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(
						new UserService(id -> Optional.of(selectedUser).filter(user -> user.getId().equals(id)))))
				.build();
	}

	@ParameterizedTest
	@EnumSource(UserRole.class)
	@DisplayName("활성 일반 사용자·관리자의 본문 없는 시작 요청은 201과 공개 시작 정보만 반환한다")
	void returnsCreatedForBothRoles(UserRole role) throws Exception {
		// given
		selectedUser = user(role);
		when(service.start(selectedUser, GAME_ID)).thenReturn(started());
		// when / then
		mvc.perform(selected(post("/api/v1/games/{gameId}/plays", GAME_ID)))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value(aMapWithSize(6)))
				.andExpect(jsonPath("$.data.playId").value(PLAY_ID.toString()))
				.andExpect(jsonPath("$.data.playToken").value("test-opaque-token"))
				.andExpect(jsonPath("$.data.ruleVersion").value("color-word-v1"))
				.andExpect(jsonPath("$.data.startedAt").value("2026-10-14T01:00:00.123456Z"))
				.andExpect(jsonPath("$.data.rules").value(aMapWithSize(5)))
				.andExpect(jsonPath("$.data.rules.initialLives").value(3))
				.andExpect(jsonPath("$.data.rules.pointsPerCorrect").value(10))
				.andExpect(jsonPath("$.data.rules.initialTimeLimitMs").value(3000))
				.andExpect(jsonPath("$.data.rules.timeLimitDecreaseMs").value(80))
				.andExpect(jsonPath("$.data.rules.minimumTimeLimitMs").value(1200))
				.andExpect(jsonPath("$.data.gameData").value(aMapWithSize(2)))
				.andExpect(jsonPath("$.data.gameData.generatorVersion").value("color-word-generator-v1"))
				.andExpect(jsonPath("$.data.gameData.seed").value(4294967295L))
				.andExpect(jsonPath("$.data.playTokenHash").doesNotExist())
				.andExpect(jsonPath("$.data.rulesSnapshot").doesNotExist());
		verify(service).start(selectedUser, GAME_ID);
	}

	@Test
	@DisplayName("헤더 누락·형식 오류·역할 불일치·게임 UUID 오류는 서비스 호출 전에 공통 오류로 거절한다")
	void rejectsInvalidContextAndPath() throws Exception {
		// given / when / then
		mvc.perform(post("/api/v1/games/{gameId}/plays", GAME_ID)).andExpect(status().isUnauthorized());
		mvc.perform(selected(post("/api/v1/games/{gameId}/plays", "not-a-uuid")))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/games/{gameId}/plays", GAME_ID)
						.header("X-User-ID", "not-a-uuid").header("X-User-Role", "USER"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/games/{gameId}/plays", GAME_ID)
						.header("X-User-ID", USER_ID).header("X-User-Role", "ADMIN"))
				.andExpect(status().isForbidden());
		verifyNoInteractions(service);
	}

	@ParameterizedTest
	@EnumSource(GameErrorCode.class)
	@DisplayName("시작 업무 오류의 상태·코드·메시지와 실패 봉투를 그대로 반환한다")
	void preservesBusinessErrorContract(GameErrorCode error) throws Exception {
		// given
		when(service.start(any(), eq(GAME_ID))).thenThrow(new BusinessException(error));
		// when / then
		mvc.perform(selected(post("/api/v1/games/{gameId}/plays", GAME_ID)))
				.andExpect(status().is(error.getStatus())).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value(error.getCode()))
				.andExpect(jsonPath("$.message").value(error.getMessage()))
				.andExpect(jsonPath("$.data").value(nullValue()));
	}

	@Test
	@DisplayName("설정·저장 장애는 원인을 공개하지 않고 공통 500으로 반환한다")
	void returnsCommonServerError() throws Exception {
		// given
		when(service.start(any(), eq(GAME_ID))).thenThrow(new IllegalStateException("test internal failure"));
		// when / then
		mvc.perform(selected(post("/api/v1/games/{gameId}/plays", GAME_ID)))
				.andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("COMMON-001"))
				.andExpect(jsonPath("$.data").value(nullValue()));
	}

	private MockHttpServletRequestBuilder selected(MockHttpServletRequestBuilder request) {
		return request.header("X-User-ID", USER_ID).header("X-User-Role", selectedUser.getRole().name());
	}

	private static User user(UserRole role) {
		return new User(USER_ID, "사용자", role, UserStatus.ACTIVE, null, null, null, null, null, null, null);
	}

	private static GamePlayStartResult started() {
		GamePlay play = new GamePlay(PLAY_ID, GAME_ID, USER_ID, "color-word-v1", RULES, "a".repeat(64),
				"color-word-generator-v1", 4294967295L, Instant.parse("2026-10-14T01:00:00.123456Z"), null, null, null);
		return new GamePlayStartResult(play, "test-opaque-token", ColorWordRules.fromSnapshot(RULES));
	}
}
