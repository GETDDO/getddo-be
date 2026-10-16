package com.getddo.api.common.ratelimit;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.getddo.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Redis에 닿지 못해도 요청을 막지 않고 통과시키며, 연결 시도가 요청을 오래 붙잡지 않는지 검증한다. */
@ApiIntegrationTest
@TestPropertySource(properties = {
		"getddo.rate-limit.enabled=true",
		"getddo.rate-limit.store=redis",
		"getddo.rate-limit.policies.mission-submission.limit=20",
		"getddo.rate-limit.policies.mission-submission.scoped-limit=1",
		"spring.data.redis.host=127.0.0.1",
		"spring.data.redis.port=1"})
class RedisDownRateLimitApiIntegrationTest {

	@Autowired
	private MockMvc mvc;

	private static MockHttpServletRequestBuilder submit(UUID user, UUID mission) {
		return post("/api/v1/missions/{missionId}/submissions", mission)
				.header("X-User-ID", user.toString())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}");
	}

	@Test
	@DisplayName("Redis에 접속할 수 없으면 한도를 넘는 횟수를 보내도 429 없이 통과시키고 각 요청이 빠르게 끝난다")
	void passesThroughWhenRedisIsDown() throws Exception {
		// given
		UUID user = UUID.randomUUID();
		UUID mission = UUID.randomUUID();
		// when
		long started = System.nanoTime();
		for (int i = 0; i < 4; i++) {
			// then
			mvc.perform(submit(user, mission)).andExpect(status().isUnauthorized());
		}
		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(8));
	}
}
