package com.getddo.db.notification;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.notification.domain.MockDeliveryStatus;
import com.getddo.core.notification.domain.NotificationDelivery;
import com.getddo.core.notification.domain.NotificationJob;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.exception.NotificationProcessingErrorCode;
import com.getddo.core.notification.exception.NotificationProcessingException;
import com.getddo.core.notification.repository.NotificationJobRepository;
import com.getddo.core.notification.service.MockNotificationSender;
import com.getddo.core.notification.service.NotificationJobService;
import com.getddo.db.common.config.JpaAuditingConfig;
import com.getddo.db.support.MySqlTestConfiguration;
import com.getddo.db.ticket.MutableClock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = NotificationJobIntegrationTest.TestApplication.class,
		properties = "spring.jpa.hibernate.ddl-auto=validate")
class NotificationJobIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-01T03:00:00Z");
	private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000301");
	private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000302");
	private static final UUID MISSING = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

	@Autowired private NotificationJobService service;
	@Autowired private NotificationJobRepository repository;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MutableClock clock;
	@Autowired private PlatformTransactionManager transactionManager;
	@MockitoBean private MockNotificationSender sender;
	private String prefix;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		prefix = "gd68-test-" + UUID.randomUUID() + "-";
		for (UUID id : List.of(USER, OTHER)) {
			jdbc.update("""
				insert into users (id, name, role, status, membership, created_at, updated_at)
				values (?, '알림 테스트', 'USER', 'ACTIVE', 'VIP', ?, ?)
				""", bytes(id), utc(NOW), utc(NOW));
		}
		when(sender.send(any())).thenReturn(MockDeliveryStatus.SENT);
	}

	@AfterEach
	void clean() {
		List<byte[]> jobs = jdbc.query("select id from notification_jobs where occurrence_key like ?",
				(row, index) -> row.getBytes("id"), prefix + "%");
		for (byte[] id : jobs) {
			jdbc.update("delete from notifications where job_id = ?", id);
			jdbc.update("delete from notification_jobs where id = ?", id);
		}
		jdbc.update("delete from users where id in (?, ?)", bytes(USER), bytes(OTHER));
	}

	@Test
	@DisplayName("같은 발생 건과 수신자 순서 변경은 같은 작업이며 다른 문구는 충돌로 거절한다")
	void idempotentRegistrationAndConflictingInput() {
		// given
		NotificationJobRequest request = request("same", NOW, List.of(USER, OTHER));
		UUID id = service.register(request);

		// when
		UUID replay = service.register(request("same", NOW, List.of(OTHER, USER, USER)));

		// then
		assertThat(replay).isEqualTo(id);
		assertThat(id.version()).isEqualTo(7);
		assertThat(count("select count(*) from notification_jobs where id = ?", bytes(id))).isEqualTo(1);
		assertThat(repository.findRequest(id)).isEqualTo(request);
		assertThatThrownBy(() -> service.register(new NotificationJobRequest(request.getOccurrenceKey(),
				request.getType(), null, null, "다른 제목", request.getBody(), null, NOW, request.getRecipientIds())))
				.isInstanceOf(NotificationProcessingException.class)
				.extracting(error -> ((NotificationProcessingException) error).getErrorCode())
				.isEqualTo(NotificationProcessingErrorCode.OCCURRENCE_CONFLICT);
		assertThat(repository.findRequest(id)).isEqualTo(request);
	}

	@Test
	@DisplayName("업무 트랜잭션이 롤백되면 발생 건의 작업도 남지 않는다")
	void registrationParticipatesInOriginalTransaction() {
		// given
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		// when
		UUID id = transaction.execute(status -> {
			UUID result = service.register(request("rollback", NOW, List.of(USER)));
			status.setRollbackOnly();
			return result;
		});

		// then
		assertThat(count("select count(*) from notification_jobs where id = ?", bytes(id))).isZero();
		assertThat(service.processNextJob()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("이전 스냅샷 뒤 다른 트랜잭션이 등록한 작업도 동일 요청은 재사용하고 다른 입력은 충돌로 거절한다")
	void duplicateRegistrationReadsCurrentRowAfterEarlierSnapshot(boolean conflicting) {
		// given
		NotificationJobRequest original = request("snapshot", NOW, List.of(USER, OTHER));
		NotificationJobRequest candidate = conflicting
				? request("snapshot", NOW, List.of(USER)) : original;
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
		TransactionTemplate separate = new TransactionTemplate(transactionManager);
		separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		UUID[] registeredId = new UUID[1];
		Runnable registration = () -> transaction.executeWithoutResult(status -> {
			assertThat(count("select count(*) from notification_jobs where occurrence_key = ?",
					original.getOccurrenceKey())).isZero();
			registeredId[0] = separate.execute(inner -> service.register(original));
			// 이전 스냅샷에서는 다른 트랜잭션이 커밋한 작업이 아직 보이지 않는다.
			assertThat(count("select count(*) from notification_jobs where occurrence_key = ?",
					original.getOccurrenceKey())).isZero();
			assertThat(service.register(candidate)).isEqualTo(registeredId[0]);
		});

		// when / then
		if (conflicting) {
			assertThatThrownBy(registration::run).isInstanceOf(NotificationProcessingException.class)
					.extracting(error -> ((NotificationProcessingException) error).getErrorCode())
					.isEqualTo(NotificationProcessingErrorCode.OCCURRENCE_CONFLICT);
		} else {
			registration.run();
		}
		assertThat(repository.findRequest(registeredId[0])).isEqualTo(original);
		assertThat(count("select count(*) from notification_jobs where occurrence_key = ?",
				original.getOccurrenceKey())).isEqualTo(1);
	}

	@Test
	@DisplayName("예약 시각 전에는 생성하지 않으며 생성·모의 발송은 읽음 상태를 변경하지 않는다")
	void respectsScheduledBoundaryAndSeparatesDeliveryFromRead() {
		// given
		Instant scheduled = NOW.plusSeconds(600);
		UUID id = service.register(request("schedule", scheduled, List.of(USER)));
		clock.set(scheduled.minusNanos(1_000));

		// when / then
		assertThat(service.processNextJob()).isFalse();
		clock.set(scheduled);
		assertThat(service.processNextJob()).isTrue();
		assertThat(jobStatus(id)).isEqualTo("COMPLETED");
		assertThat(service.processNextDelivery()).isTrue();
		assertThat(count("select count(*) from notifications where job_id = ? and is_read = false and mock_delivery_status = 'SENT'",
				bytes(id))).isEqualTo(1);
		assertThat(notificationId(id, USER).version()).isEqualTo(7);
		assertThat(service.processNextJob()).isFalse();
		assertThat(service.processNextDelivery()).isFalse();
	}

	@Test
	@DisplayName("부분 생성 후 재시도는 기존 알림 ID·읽음·발송 결과를 보존하고 누락 사용자만 추가한다")
	void retriesPartialGenerationWithoutChangingExistingNotifications() {
		// given
		NotificationJobRequest request = request("partial", NOW, List.of(USER, OTHER));
		UUID id = service.register(request);
		NotificationJob job = repository.claimNextJob(NOW).orElseThrow();
		assertThat(repository.createNotification(job, request, USER, NOW)).isTrue();
		UUID original = notificationId(id, USER);
		jdbc.update("update notifications set is_read = true, mock_delivery_status = 'SENT' where id = ?", bytes(original));
		repository.failJob(job, NOW.plusSeconds(10), "NOTIFICATION-005");

		// when / then
		clock.set(NOW.plusSeconds(10).minusNanos(1_000));
		assertThat(service.processNextJob()).isFalse();
		clock.set(NOW.plusSeconds(10));
		assertThat(service.processNextJob()).isTrue();
		assertThat(jobStatus(id)).isEqualTo("COMPLETED");
		assertThat(count("select count(*) from notifications where job_id = ?", bytes(id))).isEqualTo(2);
		assertThat(notificationId(id, USER)).isEqualTo(original);
		assertThat(count("select count(*) from notifications where id = ? and is_read = true and mock_delivery_status = 'SENT'",
				bytes(original))).isEqualTo(1);
	}

	@Test
	@DisplayName("사용자 FK 위반은 즉시 최종 실패로 기록하며 이미 저장한 알림과 확정 업무 결과를 보존한다")
	void permanentGenerationFailureDoesNotUndoCommittedBusiness() {
		// given
		UUID id = new TransactionTemplate(transactionManager).execute(status -> {
			jdbc.update("update users set name = '업무 확정' where id = ?", bytes(USER));
			return service.register(request("invalid-user", NOW, List.of(USER, MISSING)));
		});

		// when
		assertThat(service.processNextJob()).isTrue();

		// then
		assertThat(jobStatus(id)).isEqualTo("FAILED");
		assertThat(count("select attempt_count from notification_jobs where id = ?", bytes(id))).isEqualTo(1);
		assertThat(count("select count(*) from notifications where job_id = ?", bytes(id))).isEqualTo(1);
		assertThat(jdbc.queryForObject("select name from users where id = ?", String.class, bytes(USER))).isEqualTo("업무 확정");
		assertThat(jdbc.queryForObject("select last_error from notification_jobs where id = ?", String.class, bytes(id)))
				.isEqualTo("NOTIFICATION-006");
		clock.set(NOW.plusSeconds(1000));
		assertThat(service.processNextJob()).isFalse();
	}

	@Test
	@DisplayName("만료된 선점은 복구하고 이전 처리자의 생성·완료·실패 저장을 차단한다")
	void recoversExpiredJobAndFencesOldWorker() {
		// given
		NotificationJobRequest request = request("lease", NOW, List.of(USER));
		UUID id = service.register(request);
		NotificationJob old = repository.claimNextJob(NOW).orElseThrow();
		clock.set(NOW.plusSeconds(60));
		NotificationJob current = repository.claimNextJob(clock.instant()).orElseThrow();

		// when / then
		assertThat(current.getAttemptCount()).isEqualTo(2);
		assertThat(repository.createNotification(old, request, USER, clock.instant())).isFalse();
		repository.completeJob(old, clock.instant());
		repository.failJob(old, null, "NOTIFICATION-006");
		assertThat(jobStatus(id)).isEqualTo("PROCESSING");
		assertThat(repository.createNotification(current, request, USER, clock.instant())).isTrue();
		repository.completeJob(current, clock.instant());
		assertThat(jobStatus(id)).isEqualTo("COMPLETED");
		assertThat(count("select count(*) from notifications where job_id = ?", bytes(id))).isEqualTo(1);
	}

	@Test
	@DisplayName("계속 중단된 생성 작업도 선점 4회 후에는 무한 반복하지 않고 최종 실패로 남긴다")
	void stopsAfterFourAbandonedClaims() {
		// given
		UUID id = service.register(request("abandoned", NOW, List.of(USER)));

		// when
		for (int attempt = 1; attempt <= 4; attempt++) {
			assertThat(repository.claimNextJob(clock.instant()).orElseThrow().getAttemptCount()).isEqualTo(attempt);
			clock.set(clock.instant().plusSeconds(60));
		}

		// then
		assertThat(repository.claimNextJob(clock.instant())).isEmpty();
		assertThat(jobStatus(id)).isEqualTo("FAILED");
		assertThat(count("select attempt_count from notification_jobs where id = ?", bytes(id))).isEqualTo(4);
	}

	@Test
	@DisplayName("모의 발송만 10초·30초·60초 재시도하며 4번째 실패 이후 읽음·생성 결과를 유지한다")
	void exhaustsOnlyDeliveryRetriesWithoutChangingReadState() {
		// given
		UUID id = service.register(request("delivery-fail", NOW, List.of(USER)));
		service.processNextJob();
		UUID notification = notificationId(id, USER);
		jdbc.update("update notifications set is_read = true where id = ?", bytes(notification));
		when(sender.send(any())).thenThrow(new NotificationProcessingException(NotificationProcessingErrorCode.TEMPORARY_FAILURE));
		int[] delays = {10, 30, 60};

		// when / then
		for (int attempt = 1; attempt <= 4; attempt++) {
			assertThat(service.processNextDelivery()).isTrue();
			assertThat(count("select delivery_attempt_count from notifications where id = ?", bytes(notification))).isEqualTo(attempt);
			if (attempt < 4) {
				Instant next = clock.instant().plusSeconds(delays[attempt - 1]);
				assertThat(timestamp("select next_delivery_attempt_at from notifications where id = ?", bytes(notification))).isEqualTo(next);
				assertThat(service.processNextDelivery()).isFalse();
				clock.set(next);
			}
		}
		assertThat(timestamp("select next_delivery_attempt_at from notifications where id = ?", bytes(notification))).isNull();
		assertThat(jobStatus(id)).isEqualTo("COMPLETED");
		assertThat(count("select attempt_count from notification_jobs where id = ?", bytes(id))).isEqualTo(1);
		assertThat(count("select count(*) from notifications where id = ? and is_read = true and mock_delivery_status = 'FAILED'",
				bytes(notification))).isEqualTo(1);
		clock.set(clock.instant().plusSeconds(1000));
		assertThat(service.processNextDelivery()).isFalse();
	}

	@Test
	@DisplayName("발송 실패 후 성공해도 같은 알림만 갱신하고 미읽음을 유지한다")
	void succeedsAfterDeliveryRetryWithoutDuplicatingNotification() {
		// given
		UUID id = service.register(request("delivery-retry", NOW, List.of(USER)));
		service.processNextJob();
		UUID notification = notificationId(id, USER);
		when(sender.send(any())).thenReturn(MockDeliveryStatus.FAILED, MockDeliveryStatus.SENT);

		// when
		service.processNextDelivery();
		clock.set(NOW.plusSeconds(10));
		service.processNextDelivery();

		// then
		assertThat(count("select count(*) from notifications where job_id = ?", bytes(id))).isEqualTo(1);
		assertThat(count("select count(*) from notifications where id = ? and is_read = false and mock_delivery_status = 'SENT'",
				bytes(notification))).isEqualTo(1);
		assertThat(count("select delivery_attempt_count from notifications where id = ?", bytes(notification))).isEqualTo(2);
	}

	@Test
	@DisplayName("발송 선점도 장애 후 복구하며 이전 발송 차수의 늦은 결과 저장을 차단한다")
	void recoversDeliveryAndFencesOldAttempt() {
		// given
		UUID id = service.register(request("delivery-lease", NOW, List.of(USER)));
		service.processNextJob();
		NotificationDelivery old = repository.claimNextDelivery(NOW).orElseThrow();
		clock.set(NOW.plusSeconds(60));
		NotificationDelivery current = repository.claimNextDelivery(clock.instant()).orElseThrow();

		// when / then
		repository.completeDelivery(old, clock.instant());
		repository.failDelivery(old, null, "NOTIFICATION-006");
		assertThat(count("select count(*) from notifications where job_id = ? and mock_delivery_status = 'PENDING'", bytes(id))).isEqualTo(1);
		repository.completeDelivery(current, clock.instant());
		assertThat(count("select count(*) from notifications where job_id = ? and mock_delivery_status = 'SENT' and is_read = false", bytes(id))).isEqualTo(1);
	}

	@Test
	@DisplayName("동일 발생 건의 동시 등록과 두 처리자의 실행은 작업·사용자 알림을 한 건씩만 만든다")
	void concurrentRegistrationAndWorkersRemainIdempotent() throws Exception {
		// given
		NotificationJobRequest request = request("concurrent", NOW, List.of(USER));
		CountDownLatch start = new CountDownLatch(1);
		List<Future<UUID>> tasks = new ArrayList<>();

		// when
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int count = 0; count < 8; count++) {
				tasks.add(executor.submit(() -> {
					assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
					return service.register(request);
				}));
			}
			start.countDown();
			UUID id = tasks.getFirst().get(15, TimeUnit.SECONDS);
			for (Future<UUID> task : tasks) {
				assertThat(task.get(15, TimeUnit.SECONDS)).isEqualTo(id);
			}
			Future<Boolean> first = executor.submit(service::processNextJob);
			Future<Boolean> second = executor.submit(service::processNextJob);
			first.get(15, TimeUnit.SECONDS);
			second.get(15, TimeUnit.SECONDS);
			assertThat(jobStatus(id)).isEqualTo("COMPLETED");
			assertThat(count("select count(*) from notifications where job_id = ?", bytes(id))).isEqualTo(1);
			assertThat(count("select attempt_count from notification_jobs where id = ?", bytes(id))).isEqualTo(1);
		}
	}

	@Test
	@DisplayName("다른 트랜잭션이 잠근 작업은 기다리지 않고 다음 작업을 선점한다")
	void skipsLockedJob() throws Exception {
		// given
		UUID first = service.register(request("locked", NOW, List.of(USER)));
		UUID next = service.register(request("unlocked", NOW, List.of(OTHER)));
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		// when / then
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			Future<?> lock = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
				jdbc.queryForList("select id from notification_jobs where id = ? for update", bytes(first));
				locked.countDown();
				try {
					if (!release.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("잠금 해제 대기 시간 초과");
					}
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(exception);
				}
				return null;
			}));
			try {
				assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
				assertThat(repository.claimNextJob(NOW).orElseThrow().getId()).isEqualTo(next);
			} finally {
				release.countDown();
			}
			lock.get(15, TimeUnit.SECONDS);
		}
	}

	@Test
	@DisplayName("문구의 따옴표·줄바꿈과 null 링크는 JSON 저장 후에도 보존한다")
	void preservesEscapedTextAndNullableLinks() {
		// given / when
		NotificationJobRequest request = new NotificationJobRequest(prefix + "json", NotificationType.RESULT_CHANGED,
				null, null, "제목 \"따옴표\"", "내용\n다음 줄\\경로", null, NOW, List.of(USER));
		UUID id = service.register(request);

		// then
		assertThat(repository.findRequest(id)).isEqualTo(request);
		service.processNextJob();
		assertThat(jdbc.queryForObject("select body from notifications where job_id = ?", String.class, bytes(id))).isEqualTo(request.getBody());
		assertThat(jdbc.queryForObject("select link_url from notifications where job_id = ?", String.class, bytes(id))).isNull();
	}

	@Test
	@DisplayName("손상된 기존 작업 입력은 무한 선점하지 않고 입력 오류로 최종 실패 기록한다")
	void recordsMalformedStoredPayloadAsFinalFailure() {
		// given
		UUID id = service.register(request("corrupt", NOW, List.of(USER)));
		jdbc.update("update notification_jobs set payload = '{}' where id = ?", bytes(id));

		// when / then
		assertThat(service.processNextJob()).isTrue();
		assertThat(jobStatus(id)).isEqualTo("FAILED");
		assertThat(jdbc.queryForObject("select last_error from notification_jobs where id = ?", String.class, bytes(id))).isEqualTo("NOTIFICATION-003");
		assertThat(service.processNextJob()).isFalse();
	}

	private NotificationJobRequest request(String suffix, Instant scheduled, List<UUID> users) {
		return new NotificationJobRequest(prefix + suffix, NotificationType.RESULT_CHANGED,
				null, null, "제목", "내용", null, scheduled, users);
	}

	private String jobStatus(UUID id) {
		return jdbc.queryForObject("select status from notification_jobs where id = ?", String.class, bytes(id));
	}

	private UUID notificationId(UUID job, UUID user) {
		return jdbc.queryForObject("select id from notifications where job_id = ? and user_id = ?",
				(row, index) -> {
					ByteBuffer value = ByteBuffer.wrap(row.getBytes("id"));
					return new UUID(value.getLong(), value.getLong());
				}, bytes(job), bytes(user));
	}

	private long count(String sql, Object... parameters) {
		return jdbc.queryForObject(sql, Long.class, parameters);
	}

	private Instant timestamp(String sql, Object... parameters) {
		LocalDateTime value = jdbc.queryForObject(sql, LocalDateTime.class, parameters);
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@ComponentScan(basePackages = {"com.getddo.core.notification", "com.getddo.db.notification"})
	@Import({JpaAuditingConfig.class, MySqlTestConfiguration.class})
	static class TestApplication {
		@Bean
		MutableClock clock() {
			return new MutableClock(NOW);
		}

		@Bean
		TimeProvider timeProvider(Clock clock) {
			return new TimeProvider(clock);
		}
	}
}
