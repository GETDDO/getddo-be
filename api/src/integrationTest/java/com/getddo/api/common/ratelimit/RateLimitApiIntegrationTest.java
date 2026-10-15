package com.getddo.api.common.ratelimit;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.getddo.api.common.config.CorsProperties;
import com.getddo.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 요청 빈도 제한을 필터 체인부터 응답 헤더까지 전 구간으로 검증한다.
 *
 * <p>사용자 문맥 검증보다 먼저 세므로 사용자 ID 헤더만 보내 DB 시드 없이 한도에 닿게 한다. 한도 안의 요청은 사용자
 * 문맥 헤더 누락으로 401이 되고, 한도를 넘은 요청이 429가 된다.</p>
 */
@ApiIntegrationTest
@TestPropertySource(properties = {
		"getddo.rate-limit.enabled=true",
		"getddo.rate-limit.policies.mission-submission.limit=20",
		"getddo.rate-limit.policies.mission-submission.scoped-limit=2"})
class RateLimitApiIntegrationTest {

	@Autowired
	private MockMvc mvc;
	@Autowired
	private CorsProperties corsProperties;
	@Autowired
	private JdbcTemplate jdbc;

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits())
				.array();
	}

	private static MockHttpServletRequestBuilder submit(UUID user, UUID mission) {
		return post("/api/v1/missions/{missionId}/submissions", mission)
				.header("X-User-ID", user.toString())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}");
	}

	@Test
	@DisplayName("같은 사용자가 같은 미션에 한도를 넘어 제출하면 429 COMMON-006과 Retry-After를 돌려준다")
	void exceedingScopedLimitReturns429WithRetryAfter() throws Exception {
		// given
		UUID user = UUID.randomUUID();
		UUID mission = UUID.randomUUID();
		// when
		// then
		mvc.perform(submit(user, mission)).andExpect(status().isUnauthorized());
		mvc.perform(submit(user, mission)).andExpect(status().isUnauthorized());
		mvc.perform(submit(user, mission))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("COMMON-006"))
				.andExpect(header().string("Retry-After", allOf(
						org.hamcrest.Matchers.matchesPattern("\\d+"),
						org.hamcrest.Matchers.not(org.hamcrest.Matchers.is("0")))));
	}

	@Test
	@DisplayName("다른 사용자나 같은 사용자의 다른 미션은 한도에 영향받지 않는다")
	void otherUsersAndOtherMissionsAreNotLimited() throws Exception {
		// given
		UUID user = UUID.randomUUID();
		UUID mission = UUID.randomUUID();
		for (int i = 0; i < 3; i++) {
			mvc.perform(submit(user, mission));
		}
		// when
		// then
		mvc.perform(submit(user, mission)).andExpect(status().isTooManyRequests());
		mvc.perform(submit(UUID.randomUUID(), mission)).andExpect(status().isUnauthorized());
		mvc.perform(submit(user, UUID.randomUUID())).andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("사용자 ID 헤더가 없거나 UUID가 아니면 제한하지 않고 기존 사용자 문맥 검증이 처리한다")
	void invalidUserHeaderIsNotRateLimited() throws Exception {
		// given
		UUID mission = UUID.randomUUID();
		// when
		// then
		for (int i = 0; i < 5; i++) {
			mvc.perform(post("/api/v1/missions/{missionId}/submissions", mission)
							.header("Idempotency-Key", UUID.randomUUID())
							.contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().isUnauthorized());
			mvc.perform(post("/api/v1/missions/{missionId}/submissions", mission)
							.header("X-User-ID", "not-a-uuid").header("Idempotency-Key", UUID.randomUUID())
							.contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andExpect(status().is4xxClientError())
					.andExpect(status().is(org.hamcrest.Matchers.not(429)));
		}
	}

	@Test
	@DisplayName("등록된 정상 사용자의 요청도 한도를 넘으면 429이고, 거절된 요청은 제출 기록을 남기지 않는다")
	void registeredUserIsLimitedAndRejectedRequestLeavesNoRows() throws Exception {
		// given
		UUID user = UUID.randomUUID();
		UUID mission = UUID.randomUUID();
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		jdbc.update("""
				insert into users (id, name, role, status, membership, updated_at, created_at)
				values (?, 'rate-limit', 'USER', 'ACTIVE', 'vip', ?, ?)
				""", bytes(user), now, now);
		String body = "{\"answers\":[{\"questionId\":\"" + UUID.randomUUID() + "\",\"answerText\":\"x\"}]}";
		try {
			// when
			// then
			for (int i = 0; i < 2; i++) {
				mvc.perform(post("/api/v1/missions/{missionId}/submissions", mission)
								.header("X-User-ID", user.toString()).header("X-User-Role", "USER")
								.header("Idempotency-Key", UUID.randomUUID())
								.contentType(MediaType.APPLICATION_JSON).content(body))
						.andExpect(status().isNotFound())
						.andExpect(jsonPath("$.code").value("MISSION-003"));
			}
			mvc.perform(post("/api/v1/missions/{missionId}/submissions", mission)
							.header("X-User-ID", user.toString()).header("X-User-Role", "USER")
							.header("Idempotency-Key", UUID.randomUUID())
							.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isTooManyRequests())
					.andExpect(jsonPath("$.code").value("COMMON-006"))
					.andExpect(header().exists("Retry-After"));
			assertThat(jdbc.queryForObject("select count(*) from mission_submissions where user_id = ?", Long.class,
					bytes(user))).isZero();
		} finally {
			jdbc.update("delete from users where id = ?", bytes(user));
		}
	}

	@Test
	@DisplayName("프론트가 Retry-After를 읽을 수 있도록 CORS 노출 헤더에 포함한다")
	void retryAfterIsExposedToBrowsers() {
		// given
		// when
		// then
		assertThat(corsProperties.getExposedHeaders()).contains("Retry-After");
	}
}
