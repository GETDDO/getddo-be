package com.getddo.db.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/** Spring 컨텍스트가 MySQL의 연결 정보와 시작·종료를 함께 관리한다. */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlTestConfiguration {

	@Bean
	@ServiceConnection
	MySQLContainer mysqlContainer() {
		return MySqlTestContainers.create();
	}
}
