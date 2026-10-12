package com.getddo.db.entry;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.core.entry.domain.EntryCommand;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.entry.service.EntryEligibilityService;
import com.getddo.core.entry.service.EntryRecorder;
import com.getddo.core.entry.service.EntryService;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.service.TicketQueryService;
import com.getddo.core.ticket.service.TicketRefundService;
import com.getddo.core.user.domain.Membership;

import com.getddo.db.ticket.LockWaitProbe;
import com.getddo.db.ticket.MutableClock;
import com.getddo.db.ticket.TicketGrantSeeds;
import com.getddo.db.ticket.TicketIntegrationTestApplication;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응모 통합 테스트 공통 기반. 응모권·출석 통합 테스트와 같은 컨텍스트(같은 MySQL)를 쓴다.
 *
 * <p>테스트 메서드를 트랜잭션으로 감싸지 않는다. 호출자처럼 서비스가 자기 트랜잭션을 열고 커밋하므로, 각 테스트는 자기
 * 사용자·이벤트·응모권을 새로 만들고 끝나면 {@link TicketGrantSeeds#cleanUp()}으로 지운다.</p>
 */
@SpringBootTest(
		classes = TicketIntegrationTestApplication.class,
		properties = "spring.jpa.hibernate.ddl-auto=validate")
abstract class EntryIntegrationTestSupport {

	/** 시계의 초기 시각. 모집 시간은 이 시각을 가운데 두고 잡는다. */
	protected static final Instant NOW = TicketIntegrationTestApplication.INITIAL_TIME;
	/** 시계를 어디로 옮겨도 만료되지 않는 먼 만료 시각. */
	protected static final Instant FAR_EXPIRY = Instant.parse("2026-12-31T15:00:00Z");

	@Autowired
	protected EntryService entryService;
	@Autowired
	protected EntryRecorder entryRecorder;
	@Autowired
	protected EntryEligibilityService eligibilityService;
	@Autowired
	protected TicketQueryService ticketQueryService;
	@Autowired
	protected TicketRefundService ticketRefundService;
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
	void setUpEntryTest() {
		clock.set(NOW);
		seeds = new TicketGrantSeeds(jdbc);
		lockWaits = new LockWaitProbe(mysql);
		userId = seeds.user();
	}

	@AfterEach
	void cleanUpEntryTest() {
		seeds.cleanUp();
	}

	/** 응모권을 쓰는 일반 가중치 이벤트. {@code maxTicketsPerUser}가 null이면 상한 없는 월말 소진용이다. */
	protected UUID weightedEvent(Integer maxTicketsPerUser) {
		return seeds.event("TICKET", true, maxTicketsPerUser, "excellent", NOW.minusSeconds(86_400),
				NOW.plusSeconds(86_400), "OPEN", null);
	}

	/** 응모권을 쓰지만 가중치를 적용하지 않는 이벤트. 브론즈 1장만 쓸 수 있다. */
	protected UUID unweightedEvent() {
		return seeds.event("TICKET", false, 1, "excellent", NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
				"OPEN", null);
	}

	protected UUID noTicketEvent() {
		return seeds.event("NO_TICKET", false, null, "excellent", NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
				"OPEN", null);
	}

	/** 사용자에게 같은 등급의 응모권 {@code count}장을 먼 만료로 넣는다. */
	protected void giveTickets(UUID user, TicketGrade grade, int count) {
		for (int i = 0; i < count; i++) {
			seeds.ticket(user, grade.name(), FAR_EXPIRY);
		}
	}

	protected static Map<TicketGrade, Long> tickets(Object... gradeAndCount) {
		Map<TicketGrade, Long> map = new EnumMap<>(TicketGrade.class);
		for (int i = 0; i < gradeAndCount.length; i += 2) {
			map.put((TicketGrade) gradeAndCount[i], ((Number) gradeAndCount[i + 1]).longValue());
		}
		return map;
	}

	protected EntryCommand command(UUID user, UUID eventId, UUID entryId, Map<TicketGrade, Long> tickets) {
		return new EntryCommand(user, false, Membership.EXCELLENT, eventId, entryId, tickets);
	}

	protected long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	protected long usedTicketCount(UUID user, UUID eventId) {
		return count("select used_ticket_count from event_participants where user_id = ? and event_id = ?",
				bytes(user), bytes(eventId));
	}

	protected long spentTickets(UUID user) {
		return count("select count(*) from tickets where user_id = ? and status = 'SPENT'", bytes(user));
	}

	protected static void assertEntryError(Throwable thrown, EntryErrorCode expected) {
		assertThat(thrown).isInstanceOfSatisfying(EntryException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}
}
