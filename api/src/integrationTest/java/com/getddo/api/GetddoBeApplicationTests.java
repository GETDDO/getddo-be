package com.getddo.api;

import java.time.Clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.getddo.api.support.ApiIntegrationTest;
import com.getddo.db.common.config.JpaAuditingConfig;

import static org.assertj.core.api.Assertions.assertThat;

@ApiIntegrationTest
class GetddoBeApplicationTests {

	@Autowired
	private ApplicationContext context;

	@Test
	@DisplayName("MySQL 초기화 후 Auditing 설정과 Clock이 하나씩 등록된다")
	void contextLoads() {
		assertThat(context.getBeansOfType(JpaAuditingConfig.class)).hasSize(1);
		assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
	}

}
