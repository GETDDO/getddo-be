package com.getddo.db.attendance;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.db.ticket.MutableClock;
import com.getddo.db.ticket.TicketGrantSeeds;
import com.getddo.db.ticket.TicketIntegrationTestApplication;

/**
 * 출석 통합 테스트 공통 기반. 응모권 통합 테스트와 같은 컨텍스트(같은 MySQL)를 쓴다.
 *
 * <p>테스트 메서드를 트랜잭션으로 감싸지 않는다. 각 테스트는 자기 사용자·관리자·정책을 새로 만들고 끝나면 지운다.
 * 연속 출석 정책의 적용월은 DB 전체에서 하나만 둘 수 있어 정리가 필수다.</p>
 */
@SpringBootTest(
		classes = TicketIntegrationTestApplication.class,
		properties = "spring.jpa.hibernate.ddl-auto=validate")
public abstract class AttendanceIntegrationTestSupport {

	@Autowired
	protected TransactionTemplate transaction;
	@Autowired
	protected JdbcTemplate jdbc;
	@Autowired
	protected MutableClock clock;

	protected TicketGrantSeeds seeds;
	protected AttendanceSeeds policies;
	protected UUID userId;
	/** 정책 등록자로 쓰는 사용자. */
	protected UUID adminId;

	@BeforeEach
	void setUpAttendanceTest() {
		clock.set(TicketIntegrationTestApplication.INITIAL_TIME);
		seeds = new TicketGrantSeeds(jdbc);
		policies = new AttendanceSeeds(jdbc);
		userId = seeds.user();
		adminId = seeds.user();
	}

	@AfterEach
	void cleanUpAttendanceTest() {
		seeds.cleanUp();
	}

	protected long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}
}
