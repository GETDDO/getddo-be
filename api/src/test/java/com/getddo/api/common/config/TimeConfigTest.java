package com.getddo.api.common.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.getddo.core.time.TimeProvider;

import static org.assertj.core.api.Assertions.assertThat;

class TimeConfigTest {

	private static final Instant FIXED_NOW = Instant.parse("2026-09-30T15:00:00Z");
	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(TimeConfig.class);

	@Test
	void registersUtcClockAndTimeProvider() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(Clock.class);
			assertThat(context).hasSingleBean(TimeProvider.class);
			assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
		});
	}

	@Test
	void injectsSelectedFixedClockIntoTimeProvider() {
		runner.withUserConfiguration(FixedClockConfig.class).run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).hasSingleBean(TimeProvider.class);
			assertThat(context.getBean(TimeProvider.class).now()).isEqualTo(FIXED_NOW);
		});
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfig {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
		}
	}
}
