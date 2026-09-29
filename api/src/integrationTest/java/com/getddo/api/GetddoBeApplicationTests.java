package com.getddo.api;

import java.time.Clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.db.common.config.JpaAuditingConfig;

import static org.assertj.core.api.Assertions.assertThat;

// Spring 컨텍스트를 먼저 정리한 뒤 MySQL 컨테이너를 종료한다.
@Testcontainers
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GetddoBeApplicationTests {

	@Container
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

	@DynamicPropertySource
	static void configureDatabase(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
	}

	@Autowired
	private ApplicationContext context;

	@Test
	@DisplayName("MySQL 초기화 후 Auditing 설정과 Clock이 하나씩 등록된다")
	void contextLoads() {
		assertThat(context.getBeansOfType(JpaAuditingConfig.class)).hasSize(1);
		assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
	}

}
