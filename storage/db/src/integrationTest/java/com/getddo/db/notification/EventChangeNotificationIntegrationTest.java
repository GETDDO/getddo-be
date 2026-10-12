package com.getddo.db.notification;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.notification.domain.EventNotificationChange;
import com.getddo.core.notification.domain.NotificationJob;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.exception.NotificationProcessingException;
import com.getddo.core.notification.repository.NotificationJobRepository;
import com.getddo.core.notification.service.EventChangeNotificationService;
import com.getddo.core.notification.service.EventStartNotificationService;
import com.getddo.core.notification.service.NotificationJobService;
import com.getddo.db.common.util.UuidBinary;
import com.getddo.db.ticket.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = NotificationJobIntegrationTest.TestApplication.class,
		properties = "spring.jpa.hibernate.ddl-auto=validate")
class EventChangeNotificationIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
	private static final Instant OLD_START = NOW.plusSeconds(300);
	private static final Instant NEW_START = NOW.plusSeconds(3600);
	@Autowired private EventChangeNotificationService changes;
	@Autowired private EventStartNotificationService starts;
	@Autowired private NotificationJobService jobs;
	@Autowired private NotificationJobRepository repository;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MutableClock clock;
	@Autowired private PlatformTransactionManager transactionManager;
	private final List<UUID> users = new ArrayList<>();
	private UUID event;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		event = UUID.randomUUID();
		jdbc.update("""
			insert into events (id, title, description, event_type, starts_at, ends_at,
			status, created_at, updated_at, membership_rule)
			values (?, '변경 테스트', '설명', 'NO_TICKET', ?, ?, 'SCHEDULED', ?, ?, 'vip')
			""", bytes(event), utc(OLD_START), utc(NOW.plusSeconds(7200)), utc(NOW), utc(NOW));
	}

	@AfterEach
	void clean() {
		jdbc.update("delete from notifications where event_id = ?", bytes(event));
		jdbc.update("update notification_jobs set source_job_id = null where event_id = ?", bytes(event));
		jdbc.update("delete from notification_jobs where event_id = ?", bytes(event));
		jdbc.update("delete from events where id = ?", bytes(event));
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", bytes(user));
		}
	}

	@Test
	@DisplayName("시작 작업 등록 전 일정 변경은 새 시작 10분 전에 현재 자격으로 생성한다")
	void usesNewScheduleBeforeRegistration() {
		// given
		UUID user = user("EXCELLENT");
		EventNotificationChange change = scheduleChange();
		// when
		UUID changeJob = saveSchedule(change);
		// then
		assertThat(repository.findRequest(changeJob).getRecipientIds()).isEmpty();
		assertThat(starts.registerNextDueEvent()).isFalse();
		jdbc.update("update users set membership = 'VIP' where id = ?", bytes(user));
		clock.set(NEW_START.minusSeconds(600));
		assertThat(starts.registerNextDueEvent()).isTrue();
		assertThat(repository.findRequest(startJobId()).getRecipientIds()).contains(user);
		assertThat(repository.findRequest(startJobId()).getScheduledAt()).isEqualTo(NEW_START.minusSeconds(600));
	}

	@Test
	@DisplayName("아무에게도 생성하지 않은 시작 작업을 중지하고 새 일정에서 다시 등록한다")
	void replacesUnstartedGenerationWithoutDeletingHistory() {
		// given
		UUID user = user("VIP");
		UUID oldJob = registerStart(List.of(user));
		// when
		saveSchedule(scheduleChange());
		// then
		assertThat(stoppedBy(oldJob)).startsWith("event-change:");
		assertThat(repository.findRequest(oldJob).getScheduledAt()).isEqualTo(NOW);
		assertThat(starts.registerNextDueEvent()).isFalse();
		clock.set(NEW_START.minusSeconds(600));
		assertThat(starts.registerNextDueEvent()).isTrue();
		assertThat(countJobs("EVENT_START")).isEqualTo(2);
		assertThat(jobs.processNextJob()).isTrue(); // 수신자가 없는 변경 안내 작업
		assertThat(jobs.processNextJob()).isTrue(); // 새 일정의 시작 작업
		assertThat(jobs.processNextJob()).isFalse();
		assertThat(notificationCount("EVENT_START", user)).isEqualTo(1);
	}

	@Test
	@DisplayName("부분 생성 중 일정 변경은 남은 생성을 막고 이미 받은 사람에게만 안내한다")
	void stopsLeasedJobAndNotifiesOnlyExistingRecipients() {
		// given
		UUID received = user("VIP");
		UUID pending = user("VIP");
		UUID oldJob = registerStart(List.of(received, pending));
		NotificationJob lease = repository.claimNextJob(NOW).orElseThrow();
		NotificationJobRequest original = repository.findRequest(oldJob);
		assertThat(repository.createNotification(lease, original, received, NOW)).isTrue();
		jdbc.update("update notifications set is_read = true where job_id = ?", bytes(oldJob));
		jdbc.update("update users set membership = 'EXCELLENT' where id = ?", bytes(received));
		// when
		UUID changeJob = saveSchedule(scheduleChange());
		// then
		assertThat(repository.findRequest(changeJob).getRecipientIds()).containsExactly(received);
		assertThat(repository.createNotification(lease, original, pending, NOW)).isFalse();
		repository.completeJob(lease, NOW);
		repository.failJob(lease, NOW.plusSeconds(10), "NOTIFICATION-005");
		assertThat(jdbc.queryForObject("select status from notification_jobs where id = ?", String.class,
				bytes(oldJob))).isEqualTo("PROCESSING");
		UUID sourceJobId = jdbc.queryForObject("select source_job_id from notification_jobs where id = ?",
				(row, index) -> UuidBinary.fromBytes(row.getBytes("source_job_id")), bytes(changeJob));
		assertThat(sourceJobId).isEqualTo(oldJob);
		assertThat(jobs.processNextJob()).isTrue();
		assertThat(notificationCount("EVENT_SCHEDULE_CHANGED", received)).isEqualTo(1);
		assertThat(notificationCount("EVENT_SCHEDULE_CHANGED", pending)).isZero();
		clock.set(NEW_START.minusSeconds(600));
		assertThat(starts.registerNextDueEvent()).isFalse(); // 이미 시작 안내를 받은 이벤트는 재발송하지 않는다.
		assertThat(jobs.processNextJob()).isFalse();
		assertThat(jdbc.queryForObject("select is_read from notifications where job_id = ?", Boolean.class,
				bytes(oldJob))).isTrue();
	}

	@Test
	@DisplayName("시작 이후 취소도 시작 알림을 받은 사용자에게만 안내한다")
	void cancelsAfterStartAndPreservesOldNotification() {
		// given
		UUID user = user("VIP");
		UUID oldJob = registerStart(List.of(user));
		assertThat(jobs.processNextJob()).isTrue();
		clock.set(OLD_START.plusSeconds(1));
		EventNotificationChange change = EventNotificationChange.canceled(UUID.randomUUID(), event,
				"변경 테스트", clock.instant());
		// when
		UUID cancelJob = saveCancellation(change);
		// then
		assertThat(repository.findRequest(cancelJob).getRecipientIds()).containsExactly(user);
		assertThat(jobs.processNextJob()).isTrue();
		assertThat(notificationCount("EVENT_START", user)).isEqualTo(1);
		assertThat(notificationCount("EVENT_CANCELED", user)).isEqualTo(1);
		assertThat(stoppedBy(oldJob)).isEqualTo(change.getOccurrenceKey());
		assertThat(starts.registerNextDueEvent()).isFalse();
	}

	@Test
	@DisplayName("아직 생성하지 않은 시작 작업은 취소 후 선점·생성하지 않는다")
	void cancellationStopsPendingJob() {
		// given
		UUID user = user("VIP");
		UUID oldJob = registerStart(List.of(user));
		// when
		UUID cancelJob = saveCancellation(EventNotificationChange.canceled(UUID.randomUUID(), event,
				"변경 테스트", NOW));
		// then
		assertThat(repository.findRequest(cancelJob).getRecipientIds()).isEmpty();
		assertThat(stoppedBy(oldJob)).isNotNull();
		assertThat(jobs.processNextJob()).isTrue();
		assertThat(jobs.processNextJob()).isFalse();
		assertThat(notificationCount("EVENT_START", user)).isZero();
		assertThat(starts.registerNextDueEvent()).isFalse();
	}

	@Test
	@DisplayName("원본 변경 트랜잭션 롤백 시 일정·작업 중지·변경 안내가 함께 롤백된다")
	void rollsBackWithOriginalBusinessTransaction() {
		// given
		UUID user = user("VIP");
		UUID oldJob = registerStart(List.of(user));
		EventNotificationChange change = scheduleChange();
		// when / then
		assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
			updateSchedule(change);
			changes.register(change);
			throw new IllegalStateException("rollback");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(stoppedBy(oldJob)).isNull();
		assertThat(countJobs("EVENT_SCHEDULE_CHANGED")).isZero();
		assertThat(jdbc.queryForObject("select starts_at from events where id = ?", LocalDateTime.class,
				bytes(event))).isEqualTo(utc(OLD_START));
		assertThat(jobs.processNextJob()).isTrue();
		assertThat(notificationCount("EVENT_START", user)).isEqualTo(1);
	}

	@Test
	@DisplayName("동일 발생 건의 재요청은 같은 작업을 반환하고 다른 입력은 충돌로 거절한다")
	void preservesIdempotencyAndRejectsConflictingInput() {
		// given
		EventNotificationChange change = scheduleChange();
		UUID job = saveSchedule(change);
		// when / then
		assertThat(changes.register(change)).isEqualTo(job);
		assertThatThrownBy(() -> changes.register(EventNotificationChange.scheduleChanged(change.getOccurrenceId(),
				event, "다른 제목", OLD_START, NEW_START, NOW))).isInstanceOf(NotificationProcessingException.class)
				.extracting(failure -> ((NotificationProcessingException) failure).getErrorCode().getCode())
				.isEqualTo("NOTIFICATION-004");
		assertThat(countJobs("EVENT_SCHEDULE_CHANGED")).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 변경의 동시 등록도 안내 작업을 하나만 생성한다")
	void concurrentReplayRegistersOnce() throws Exception {
		// given
		EventNotificationChange change = scheduleChange();
		transaction().executeWithoutResult(status -> updateSchedule(change));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch gate = new CountDownLatch(1);
		// when
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> registerAtGate(change, ready, gate));
			var second = executor.submit(() -> registerAtGate(change, ready, gate));
			boolean bothReady = ready.await(10, TimeUnit.SECONDS);
			gate.countDown();
			// then
			assertThat(bothReady).isTrue();
			assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
		}
		assertThat(countJobs("EVENT_SCHEDULE_CHANGED")).isEqualTo(1);
	}

	@Test
	@DisplayName("아직 저장하지 않은 변경 사실은 거절하고 시작 작업을 그대로 둔다")
	void rejectsChangeBeforeEventSave() {
		// given
		UUID oldJob = registerStart(List.of(user("VIP")));
		// when / then
		assertThatThrownBy(() -> changes.register(scheduleChange())).isInstanceOf(NotificationProcessingException.class);
		assertThat(stoppedBy(oldJob)).isNull();
		assertThat(countJobs("EVENT_SCHEDULE_CHANGED")).isZero();
	}

	@Test
	@DisplayName("호출자의 이전 조회 스냅샷에도 다른 트랜잭션에서 등록한 동일 변경을 확인한다")
	void replaysWithStaleCallerSnapshot() {
		// given
		EventNotificationChange change = scheduleChange();
		TransactionTemplate other = transaction();
		other.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		TransactionTemplate oldView = transaction();
		oldView.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		// when / then
		oldView.executeWithoutResult(status -> {
			assertThat(countJobs("EVENT_SCHEDULE_CHANGED")).isZero();
			UUID registered = other.execute(inner -> {
				updateSchedule(change);
				return changes.register(change);
			});
			assertThat(changes.register(change)).isEqualTo(registered);
		});
		assertThat(countJobs("EVENT_SCHEDULE_CHANGED")).isEqualTo(1);
	}

	private UUID registerAtGate(EventNotificationChange change, CountDownLatch ready, CountDownLatch gate)
			throws InterruptedException {
		ready.countDown();
		if (!gate.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("gate timeout");
		}
		return changes.register(change);
	}

	private EventNotificationChange scheduleChange() {
		return EventNotificationChange.scheduleChanged(UUID.randomUUID(), event, "변경 테스트", OLD_START, NEW_START, NOW);
	}

	private UUID saveSchedule(EventNotificationChange change) {
		return transaction().execute(status -> {
			updateSchedule(change);
			return changes.register(change);
		});
	}

	private void updateSchedule(EventNotificationChange change) {
		jdbc.update("update events set starts_at = ? where id = ?", utc(change.getStartsAt()), bytes(event));
	}

	private UUID saveCancellation(EventNotificationChange change) {
		return transaction().execute(status -> {
			jdbc.update("update events set status = 'CANCELED' where id = ?", bytes(event));
			return changes.register(change);
		});
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private UUID registerStart(List<UUID> recipients) {
		return jobs.register(new NotificationJobRequest("test-start:" + event, NotificationType.EVENT_START,
				event, null, "기존 시작 안내", "기존 안내", "/events/" + event, NOW, recipients));
	}

	private UUID startJobId() {
		return jdbc.queryForObject("""
			select id from notification_jobs where event_id = ? and notification_type = 'EVENT_START'
			and json_extract(payload, '$.generationStoppedBy') is null
			""", (row, index) -> UuidBinary.fromBytes(row.getBytes("id")), bytes(event));
	}

	private String stoppedBy(UUID job) {
		return jdbc.queryForObject("select json_unquote(json_extract(payload, '$.generationStoppedBy')) "
				+ "from notification_jobs where id = ?", String.class, bytes(job));
	}

	private long countJobs(String type) {
		return jdbc.queryForObject("select count(*) from notification_jobs where event_id = ? and notification_type = ?",
				Long.class, bytes(event), type);
	}

	private long notificationCount(String type, UUID user) {
		return jdbc.queryForObject("""
			select count(*) from notifications n join notification_jobs j on j.id = n.job_id
			where j.event_id = ? and j.notification_type = ? and n.user_id = ?
			""", Long.class, bytes(event), type, bytes(user));
	}

	private UUID user(String membership) {
		UUID id = UUID.randomUUID();
		users.add(id);
		jdbc.update("""
			insert into users (id, name, role, status, membership, created_at, updated_at)
			values (?, '변경 알림 테스트', 'USER', 'ACTIVE', ?, ?, ?)
			""", bytes(id), membership, utc(NOW), utc(NOW));
		return id;
	}

	private static byte[] bytes(UUID id) {
		return UuidBinary.toBytes(id);
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
