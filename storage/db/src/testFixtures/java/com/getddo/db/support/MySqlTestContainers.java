package com.getddo.db.support;

import org.testcontainers.mysql.MySQLContainer;

/** 각 테스트 컨텍스트 또는 JUnit 테스트가 독립적으로 소유할 MySQL을 생성한다. */
public final class MySqlTestContainers {

	private MySqlTestContainers() {
	}

	/** 시작하지 않은 새 컨테이너를 반환하며 수명은 호출한 테스트 설정에서 관리한다. */
	public static MySQLContainer create() {
		return new MySQLContainer("mysql:8.4");
	}
}
