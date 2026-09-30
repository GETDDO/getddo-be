package com.getddo.db.ticket;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.db.common.config.JpaAuditingConfig;
import com.getddo.db.support.MySqlTestConfiguration;

/**
 * 응모권 통합 테스트 컨텍스트.
 *
 * <p>이 클래스의 패키지가 자동 설정 기준 패키지가 되어 응모권 Entity와 JPA Repository만 등록된다.
 * core의 응모권 Service와 storage:db의 저장소 구현을 함께 올려 실제 호출 경로를 검증한다.</p>
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = {"com.getddo.core.ticket", "com.getddo.db.ticket"})
@Import({JpaAuditingConfig.class, MySqlTestConfiguration.class})
public class TicketIntegrationTestApplication {

	static final Instant INITIAL_TIME = Instant.parse("2026-09-15T03:00:00Z");

	@Bean
	MutableClock clock() {
		return new MutableClock(INITIAL_TIME);
	}

	@Bean
	TimeProvider timeProvider(Clock clock) {
		return new TimeProvider(clock);
	}
}
