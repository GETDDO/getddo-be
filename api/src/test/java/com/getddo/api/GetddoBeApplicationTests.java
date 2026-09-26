package com.getddo.api;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import com.getddo.db.common.config.JpaAuditingConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.flyway.enabled=false")
class GetddoBeApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	void contextLoads() {
		assertThat(context.getBeansOfType(JpaAuditingConfig.class)).hasSize(1);
		assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
	}

}
