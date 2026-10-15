package com.getddo.api.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.getddo.api.common.ratelimit.InMemoryRateLimiter;
import com.getddo.api.common.ratelimit.RateLimitInterceptor;
import com.getddo.api.common.ratelimit.RateLimitProperties;
import com.getddo.api.common.ratelimit.RateLimiter;
import com.getddo.core.common.time.TimeProvider;

/**
 * 요청 빈도 제한을 MVC에 등록한다.
 *
 * <p>제한기는 기본으로 서버 메모리 구현을 쓴다. 다른 {@link RateLimiter} 빈(예: Redis)을 등록하면 그것이 우선한다.</p>
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig implements WebMvcConfigurer {

	private final RateLimiter rateLimiter;
	private final RateLimitProperties properties;

	public RateLimitConfig(RateLimiter rateLimiter, RateLimitProperties properties) {
		this.rateLimiter = rateLimiter;
		this.properties = properties;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new RateLimitInterceptor(rateLimiter, properties)).addPathPatterns("/api/**");
	}

	/** 다른 구현이 없을 때 쓰는 서버 메모리 제한기. */
	@Configuration
	static class DefaultRateLimiterConfig {

		@Bean
		@ConditionalOnMissingBean(RateLimiter.class)
		RateLimiter inMemoryRateLimiter(TimeProvider timeProvider) {
			return new InMemoryRateLimiter(timeProvider);
		}
	}
}
