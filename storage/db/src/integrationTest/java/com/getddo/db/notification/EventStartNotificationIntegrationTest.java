package com.getddo.db.notification;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.repository.NotificationJobRepository;
import com.getddo.core.notification.service.EventStartNotificationService;
import com.getddo.core.notification.service.NotificationJobService;
import com.getddo.db.ticket.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = NotificationJobIntegrationTest.TestApplication.class,
		properties = "spring.jpa.hibernate.ddl-auto=validate")
class EventStartNotificationIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-01T03:00:00Z");
	@Autowired private EventStartNotificationService service;
	@Autowired private NotificationJobService jobs;
	@Autowired private NotificationJobRepository repository;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MutableClock clock;
	@Autowired private PlatformTransactionManager transactionManager;
	private final List<UUID> events = new ArrayList<>();
	private final List<UUID> users = new ArrayList<>();

	@BeforeEach
	void setUp() {
		clock.set(NOW);
	}

	/** 이번 테스트가 만든 데이터만 외래 키 의존 순서로 정리한다. */
	@AfterEach
	void clean() {
		for (UUID event : events) {
			jdbc.update("delete from notifications where event_id = ?", bytes(event));
			jdbc.update("delete from notification_jobs where event_id = ?", bytes(event));
			jdbc.update("delete from events where id = ?", bytes(event));
		}
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", bytes(user));
		}
	}

	@ParameterizedTest
	@CsvSource({"601,false", "600,true", "300,true", "1,true", "0,false", "-1,false"})
	@DisplayName("시작 10분 전부터 시작 직전까지만 작업을 등록한다")
	void respectsTimeWindow(long secondsUntilStart, boolean expected) {
		event(secondsUntilStart, "vip", "SCHEDULED", false);
		assertThat(service.registerNextDueEvent()).isEqualTo(expected);
		assertThat(service.registerNextDueEvent()).isFalse();
	}

	@ParameterizedTest
	@CsvSource({"CANCELED,false", "OPEN,false", "SCHEDULED,true"})
	@DisplayName("취소·진행 중·삭제된 이벤트에는 시작 작업을 등록하지 않는다")
	void ignoresUnavailableEvents(String status, boolean deleted) {
		event(600, "excellent", status, deleted);
		assertThat(service.registerNextDueEvent()).isFalse();
	}

	@ParameterizedTest
	@CsvSource({"excellent,3", "vip,2", "vvip,1"})
	@DisplayName("현재 최소 등급 이상의 활성 일반 사용자만 받는다")
	void selectsEligibleUsers(String rule, int expectedCount) {
		UUID excellent = user("EXCELLENT", "USER", "ACTIVE");
		UUID vip = user("VIP", "USER", "ACTIVE");
		UUID vvip = user("VVIP", "USER", "ACTIVE");
		user("VVIP", "ADMIN", "ACTIVE");
		user("VVIP", "USER", "INACTIVE");
		user(null, "USER", "ACTIVE");
		UUID event = event(600, rule, "SCHEDULED", false);

		assertThat(service.registerNextDueEvent()).isTrue();
		List<UUID> recipients = request(event).getRecipientIds();
		assertThat(recipients).hasSize(expectedCount).contains(vvip);
		if (expectedCount >= 2) {
			assertThat(recipients).contains(vip);
		}
		if (expectedCount == 3) {
			assertThat(recipients).contains(excellent);
		}
	}

	@Test
	@DisplayName("이벤트 등록 때 대상이 아니었어도 시작 알림 시점에 승급했으면 받는다")
	void looksUpMembershipWhenDue() {
		UUID user = user("EXCELLENT", "USER", "ACTIVE");
		UUID event = event(601, "vip", "SCHEDULED", false);
		assertThat(service.registerNextDueEvent()).isFalse();
		jdbc.update("update users set membership = 'VIP' where id = ?", bytes(user));
		clock.set(NOW.plusSeconds(1));
		assertThat(service.registerNextDueEvent()).isTrue();
		assertThat(request(event).getRecipientIds()).containsExactly(user);
	}

	@Test
	@DisplayName("작업 생성 이후 대상 변경·반복 폴링에도 최초 대상에게 한 번만 생성한다")
	void preservesSnapshotAndDoesNotDuplicate() {
		UUID user = user("VIP", "USER", "ACTIVE");
		UUID event = event(300, "vip", "SCHEDULED", false);
		assertThat(service.registerNextDueEvent()).isTrue();
		jdbc.update("update users set membership = 'EXCELLENT' where id = ?", bytes(user));
		user("VVIP", "USER", "ACTIVE");
		assertThat(service.registerNextDueEvent()).isFalse();
		assertThat(jobs.processNextJob()).isTrue();
		assertThat(jobs.processNextJob()).isFalse();
		assertThat(request(event).getRecipientIds()).containsExactly(user);
		assertThat(jdbc.queryForObject("select count(*) from notifications where event_id = ?",
				Long.class, bytes(event))).isEqualTo(1);
	}

	@Test
	@DisplayName("대상이 없어도 작업을 완료해 같은 이벤트를 무한 등록하지 않는다")
	void completesWithoutRecipients() {
		UUID event = event(600, "vvip", "SCHEDULED", false);
		assertThat(service.registerNextDueEvent()).isTrue();
		assertThat(jobs.processNextJob()).isTrue();
		assertThat(service.registerNextDueEvent()).isFalse();
		assertThat(request(event).getRecipientIds()).isEmpty();
		assertThat(jdbc.queryForObject("select status from notification_jobs where event_id = ?",
				String.class, bytes(event))).isEqualTo("COMPLETED");
	}

	@Test
	@DisplayName("작업 등록 트랜잭션이 롤백되면 다음 폴링에서 다시 등록할 수 있다")
	void registersAgainAfterRollback() {
		UUID event = event(600, "vip", "SCHEDULED", false);
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			assertThat(service.registerNextDueEvent()).isTrue();
			throw new IllegalStateException("rollback");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(service.registerNextDueEvent()).isTrue();
		assertThat(request(event).getEventId()).isEqualTo(event);
	}

	@Test
	@DisplayName("두 처리자가 동시에 폴링해도 이벤트의 시작 작업은 하나만 남는다")
	void registersOnceAcrossConcurrentPollers() throws Exception {
		UUID event = event(600, "vip", "SCHEDULED", false);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch gate = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> {
				ready.countDown();
				if (!gate.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("gate timeout");
				}
				return service.registerNextDueEvent();
			});
			var second = executor.submit(() -> {
				ready.countDown();
				if (!gate.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("gate timeout");
				}
				return service.registerNextDueEvent();
			});
			boolean bothReady = ready.await(10, TimeUnit.SECONDS);
			gate.countDown();
			assertThat(bothReady).isTrue();
			assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(true, false);
		}
		assertThat(jdbc.queryForObject("select count(*) from notification_jobs where event_id = ?",
				Long.class, bytes(event))).isEqualTo(1);
	}

	/** 테스트 이벤트의 저장된 작업 입력을 기존 저장소를 통해 확인한다. */
	private NotificationJobRequest request(UUID event) {
		UUID job = jdbc.queryForObject("select id from notification_jobs where event_id = ?",
				(row, index) -> uuid(row.getBytes("id")), bytes(event));
		return repository.findRequest(job);
	}

	private UUID user(String membership, String role, String status) {
		UUID id = UUID.randomUUID();
		users.add(id);
		jdbc.update("""
			insert into users (id, name, role, status, membership, created_at, updated_at)
			values (?, '시작 알림 테스트', ?, ?, ?, ?, ?)
			""", bytes(id), role, status, membership, utc(NOW), utc(NOW));
		return id;
	}

	private UUID event(long secondsUntilStart, String rule, String status, boolean deleted) {
		UUID id = UUID.randomUUID();
		events.add(id);
		Instant startsAt = NOW.plusSeconds(secondsUntilStart);
		jdbc.update("""
			insert into events (id, title, description, event_type, starts_at, ends_at,
			status, created_at, updated_at, deleted_at, membership_rule)
			values (?, '시작 알림 이벤트', '설명', 'NO_TICKET', ?, ?, ?, ?, ?, ?, ?)
			""", bytes(id), utc(startsAt), utc(startsAt.plusSeconds(3600)), status,
				utc(NOW), utc(NOW), deleted ? utc(NOW) : null, rule);
		return id;
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}

	private static UUID uuid(byte[] value) {
		ByteBuffer buffer = ByteBuffer.wrap(value);
		return new UUID(buffer.getLong(), buffer.getLong());
	}
}
