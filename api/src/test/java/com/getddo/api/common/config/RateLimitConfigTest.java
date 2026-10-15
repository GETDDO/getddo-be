package com.getddo.api.common.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.getddo.api.common.ratelimit.InMemoryRateLimiter;
import com.getddo.api.common.ratelimit.RateLimiter;
import com.getddo.api.common.ratelimit.RedisRateLimiter;
import com.getddo.core.common.time.TimeProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withBean(TimeProvider.class, () -> new TimeProvider(Clock.system(ZoneOffset.UTC)))
			.withUserConfiguration(RateLimitConfig.class);

	@Test
	@DisplayName("저장소 설정이 없으면 서버 메모리 제한기를 쓴다")
	void defaultsToMemory() {
		runner.run(context -> assertThat(context.getBean(RateLimiter.class)).isInstanceOf(InMemoryRateLimiter.class));
	}

	@Test
	@DisplayName("store=memory면 서버 메모리 제한기를 쓰고 Redis 빈이 있어도 쓰지 않는다")
	void memoryStore() {
		runner.withPropertyValues("getddo.rate-limit.store=memory")
				.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
				.run(context -> assertThat(context.getBeansOfType(RateLimiter.class).values())
						.singleElement().isInstanceOf(InMemoryRateLimiter.class));
	}

	@Test
	@DisplayName("store=redis면 Redis 제한기 하나만 만들고 메모리 제한기는 만들지 않는다")
	void redisStore() {
		runner.withPropertyValues("getddo.rate-limit.store=redis")
				.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
				.run(context -> assertThat(context.getBeansOfType(RateLimiter.class).values())
						.singleElement().isInstanceOf(RedisRateLimiter.class));
	}

	@Test
	@DisplayName("store=redis인데 Redis 템플릿이 없으면 시작 단계에서 실패해 잘못된 설정을 바로 알린다")
	void redisStoreWithoutRedisFailsAtStartup() {
		runner.withPropertyValues("getddo.rate-limit.store=redis")
				.run(context -> assertThat(context).hasFailed());
	}
}
