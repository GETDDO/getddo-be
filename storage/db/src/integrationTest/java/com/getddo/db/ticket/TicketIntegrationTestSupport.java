package com.getddo.db.ticket;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.core.ticket.domain.GrantCommand;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.service.TicketGrantService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;

/**
 * 응모권 통합 테스트 공통 기반.
 *
 * <p>테스트 메서드를 트랜잭션으로 감싸지 않는다. 호출자처럼 {@link TransactionTemplate}으로 트랜잭션을 직접 열고
 * 커밋하므로, 각 테스트는 자기 사용자와 청구를 새로 만들고 끝나면 {@link TicketGrantSeeds#cleanUp()}으로 지운다.</p>
 */
@SpringBootTest(
		classes = TicketIntegrationTestApplication.class,
		properties = "spring.jpa.hibernate.ddl-auto=validate")
abstract class TicketIntegrationTestSupport {

	@Autowired
	protected TicketGrantService grantService;
	@Autowired
	protected TransactionTemplate transaction;
	@Autowired
	protected JdbcTemplate jdbc;
	@Autowired
	protected MutableClock clock;
	@Autowired
	private MySQLContainer mysql;

	protected TicketGrantSeeds seeds;
	protected LockWaitProbe lockWaits;
	protected UUID userId;

	@BeforeEach
	void setUpTicketTest() {
		clock.set(TicketIntegrationTestApplication.INITIAL_TIME);
		seeds = new TicketGrantSeeds(jdbc);
		lockWaits = new LockWaitProbe(mysql);
		userId = seeds.user();
	}

	@AfterEach
	void cleanUpTicketTest() {
		seeds.cleanUp();
	}

	protected static GrantCommand command(UUID userId, GrantSourceType type, UUID claimId, long quantity) {
		return new GrantCommand(userId, new GrantSource(type, claimId), quantity, "테스트 보상");
	}

	/** 호출자처럼 한 트랜잭션에서 미션 청구를 저장하고 지급한다. */
	protected GrantResult grantNewMissionClaim(UUID user, int quantity) {
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(user);
		return transaction.execute(status -> {
			UUID claimId = seeds.missionClaim(user, parents, quantity);
			return grantService.grant(command(user, GrantSourceType.MISSION, claimId, quantity));
		});
	}

	protected long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	protected long ticketCount(UUID user) {
		return count("select count(*) from tickets where user_id = ?", bytes(user));
	}

	protected long historyCount(UUID user) {
		return count("""
				select count(*) from ticket_histories h
				join tickets t on t.id = h.ticket_id where t.user_id = ?
				""", bytes(user));
	}

	/**
	 * 지급으로는 만들 수 없는 상태(사용·만료 처리된 응모권 등)의 응모권을 미션 청구에 직접 넣는다.
	 * 시각은 UTC 원값으로 저장한다.
	 */
	protected UUID insertTicket(UUID user, UUID missionClaimId, TicketGrade grade, TicketStatus status,
			String expiresAt) {
		UUID id = UUID.randomUUID();
		LocalDateTime createdAt = LocalDateTime.parse("2026-07-01T00:00:00");
		jdbc.update("""
				insert into tickets (id, user_id, mission_reward_claim_id, grade, status, expires_at, version,
				  created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, 1, ?, ?)
				""", bytes(id), bytes(user), bytes(missionClaimId), grade.name(), status.name(),
				LocalDateTime.ofInstant(Instant.parse(expiresAt), ZoneOffset.UTC), createdAt, createdAt);
		return id;
	}

	/** DB에 저장된 DATETIME 원값을 UTC 순간으로 읽는다. */
	protected Instant utc(String sql, Object... args) {
		return jdbc.queryForObject(sql, LocalDateTime.class, args).toInstant(ZoneOffset.UTC);
	}
}
