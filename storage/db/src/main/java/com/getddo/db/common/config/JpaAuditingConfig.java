package com.getddo.db.common.config;

import java.time.Clock;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** 공통 Clock을 JPA Auditing에 연결한다. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "jpaAuditingDateTimeProvider", modifyOnCreate = true)
public class JpaAuditingConfig {

	/**
	 * 감사 시각을 제공한다.
	 *
	 * @param clock 공통 시계
	 * @return 호출 시점의 시각 공급자
	 */
	@Bean
	public DateTimeProvider jpaAuditingDateTimeProvider(Clock clock) {
		return () -> Optional.of(clock.instant());
	}
}
