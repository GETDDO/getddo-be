package com.getddo.db.ticket;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

import org.testcontainers.mysql.MySQLContainer;

/**
 * InnoDB에서 잠금을 기다리는 트랜잭션 수를 확인한다.
 *
 * <p>동시성 테스트가 "다른 트랜잭션이 실제로 잠금에 막혀 있는 순간"을 기다린 뒤 다음 단계로 넘어가게 해
 * 실행 순서를 재현 가능하게 만든다. {@code information_schema.innodb_trx}는 PROCESS 권한이 필요하므로
 * 테스트 컨테이너의 root 계정으로 읽는다.</p>
 */
class LockWaitProbe {

	private static final Duration TIMEOUT = Duration.ofSeconds(20);
	private static final long POLL_MILLIS = 20;

	private final MySQLContainer mysql;

	LockWaitProbe(MySQLContainer mysql) {
		this.mysql = mysql;
	}

	/** 잠금을 기다리는 트랜잭션이 {@code expected}개 이상이 될 때까지 기다린다. */
	void awaitLockWaits(int expected) throws SQLException, InterruptedException {
		Instant deadline = Instant.now().plus(TIMEOUT);
		try (Connection root = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
				Statement statement = root.createStatement()) {
			while (Instant.now().isBefore(deadline)) {
				try (ResultSet rows = statement.executeQuery(
						"select count(*) from information_schema.innodb_trx where trx_state = 'LOCK WAIT'")) {
					rows.next();
					if (rows.getInt(1) >= expected) {
						return;
					}
				}
				Thread.sleep(POLL_MILLIS);
			}
		}
		throw new AssertionError("잠금 대기 트랜잭션이 " + expected + "개가 되지 않았다.");
	}
}
