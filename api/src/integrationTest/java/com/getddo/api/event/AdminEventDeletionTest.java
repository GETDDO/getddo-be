package com.getddo.api.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.api.support.ApiIntegrationTest;
import com.getddo.db.common.util.UuidBinary;
import com.getddo.core.event.service.EventDeletionService;
import com.getddo.core.event.exception.EventException;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.repository.NotificationJobRepository;
import com.getddo.core.notification.service.EventChangeNotificationService;
import com.getddo.core.notification.service.NotificationJobService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiIntegrationTest
class AdminEventDeletionTest {
	private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
	private static final Instant START = NOW.plusSeconds(3600);
	@Autowired private MockMvc mvc;
	@MockitoSpyBean private JdbcTemplate jdbc;
	@Autowired private MySQLContainer mysql;
	@Autowired private EventDeletionService deletions;
	@Autowired private PlatformTransactionManager transactions;
	@Autowired private NotificationJobService jobs;
	@Autowired private NotificationJobRepository jobRepository;
	@MockitoBean private Clock clock;
	@MockitoSpyBean private EventChangeNotificationService notifications;
	private UUID admin;
	private UUID user;
	private UUID event;
	private UUID prize;
	private UUID participant;
	private UUID additionalRecipient;

	@BeforeEach
	void setUp() {
		when(clock.instant()).thenReturn(NOW);
		when(clock.getZone()).thenReturn(ZoneOffset.UTC);
		admin = user("ADMIN");
		user = user("USER");
		event = UUID.randomUUID();
		prize = UUID.randomUUID();
		participant = UUID.randomUUID();
		additionalRecipient = null;
		jdbc.update("""
			insert into events(id,title,description,event_type,weighting_enabled,membership_rule,
			starts_at,ends_at,status,created_at,updated_at)
			values(?,'삭제 이벤트','설명','NO_TICKET',false,'vip',?,?,'SCHEDULED',?,?)
			""", bytes(event), utc(START), utc(START.plusSeconds(3600)), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into event_prizes(id,event_id,prize_rank,name,winner_count,created_at,updated_at)
			values(?,?,1,'보존 경품',1,?,?)
			""", bytes(prize), bytes(event), utc(NOW), utc(NOW));
	}

	@AfterEach
	void cleanUp() {
		jdbc.update("delete from notifications where event_id=?", bytes(event));
		jdbc.update("update notification_jobs set source_job_id=null where event_id=?", bytes(event));
		jdbc.update("delete from notification_jobs where event_id=?", bytes(event));
		jdbc.update("delete from event_entries where participant_id=?", bytes(participant));
		jdbc.update("delete from event_participants where event_id=?", bytes(event));
		jdbc.update("delete from event_prizes where event_id=?", bytes(event));
		jdbc.update("delete from events where id=?", bytes(event));
		jdbc.update("delete from users where id in (?,?)", bytes(admin), bytes(user));
		if (additionalRecipient != null) {
			jdbc.update("delete from users where id=?", bytes(additionalRecipient));
		}
	}

	@Test
	@DisplayName("이벤트는 삭제 시각만 기록하고 경품을 보존하며 사용자·관리자 목록과 상세에서 제외한다")
	void softDeletionHidesEventAndPreservesPrizes() throws Exception {
		// given / when
		remove(admin, "ADMIN").andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data").doesNotExist());
		// then
		assertThat(deletedAt()).isEqualTo(utc(NOW));
		assertThat(jdbc.queryForObject("select name from event_prizes where id=?", String.class, bytes(prize)))
				.isEqualTo("보존 경품");
		for (String prefix : new String[]{"/api/v1/events", "/api/v1/admin/events"}) {
			mvc.perform(get(prefix).header("X-User-ID", admin).header("X-User-Role", "ADMIN"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
			mvc.perform(get(prefix + "/" + event).header("X-User-ID", admin).header("X-User-Role", "ADMIN"))
					.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EVENT-007"));
		}
	}

	@Test
	@DisplayName("삭제 재요청은 찾을 수 없음으로 응답하고 최초 삭제 시각을 바꾸지 않는다")
	void repeatedDeletionDoesNotChangeDeletionTime() throws Exception {
		// given
		remove(admin, "ADMIN").andExpect(status().isOk());
		when(clock.instant()).thenReturn(NOW.plusSeconds(10));
		// when / then
		remove(admin, "ADMIN").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("EVENT-007"));
		assertThat(deletedAt()).isEqualTo(utc(NOW));
	}

	@Test
	@DisplayName("시작 시각에 도달한 진행 예정 이벤트의 삭제는 409로 거절한다")
	void rejectsStartBoundary() throws Exception {
		// given
		jdbc.update("update events set starts_at=? where id=?", utc(NOW), bytes(event));
		// when / then
		remove(admin, "ADMIN").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVENT-013"));
		assertThat(deletedAt()).isNull();
	}

	@Test
	@DisplayName("시작 전이라도 응모 이력이 있으면 원본 이벤트·응모를 보존하고 삭제를 거절한다")
	void rejectsExistingParticipation() throws Exception {
		// given
		participation();
		// when / then
		remove(admin, "ADMIN").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EVENT-013"));
		assertThat(deletedAt()).isNull();
		assertThat(jdbc.queryForObject("select count(*) from event_entries where participant_id=?", Long.class,
				bytes(participant))).isEqualTo(1);
	}

	@Test
	@DisplayName("일반 사용자의 삭제는 403으로 거절하며 이벤트를 변경하지 않는다")
	void rejectsNonAdmin() throws Exception {
		// given / when / then
		remove(user, "USER").andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("EVENT-006"));
		assertThat(deletedAt()).isNull();
	}

	@Test
	@DisplayName("동일 이벤트의 동시 삭제는 한 번만 성공하고 나머지는 404로 처리한다")
	void concurrentDeletionCommitsOnce() throws Exception {
		// given
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var call = (java.util.concurrent.Callable<Integer>) () -> {
				ready.countDown();
				if (!start.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("동시 요청 준비 실패"); }
				return remove(admin, "ADMIN").andReturn().getResponse().getStatus();
			};
			var first = executor.submit(call);
			var second = executor.submit(call);
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			// when
			start.countDown();
			// then
			assertThat(java.util.List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder(200, 404);
		}
		assertThat(deletedAt()).isEqualTo(utc(NOW));
	}

	@Test
	@DisplayName("이전 스냅샷이 응모 없음이어도 나중에 확정된 응모를 최신 잠금 읽기로 확인해 삭제를 거절한다")
	void readsCurrentParticipationAfterEarlierSnapshot() {
		// given
		new TransactionTemplate(transactions).executeWithoutResult(transaction -> {
			assertThat(jdbc.queryForObject("select count(*) from event_participants where event_id=?",
					Long.class, bytes(event))).isZero();
			try (var executor = Executors.newSingleThreadExecutor()) {
				executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(ignored -> participation()))
						.get(10, TimeUnit.SECONDS);
			} catch (Exception exception) {
				throw new IllegalStateException("응모 확정 테스트 실패", exception);
			}
			// when / then
			assertThatThrownBy(() -> deletions.delete(event, admin)).isInstanceOf(EventException.class)
					.satisfies(exception -> assertThat(((EventException) exception).getErrorCode())
							.isEqualTo(EventErrorCode.DELETION_NOT_ALLOWED));
			transaction.setRollbackOnly();
		});
		assertThat(deletedAt()).isNull();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("삭제는 대기·처리 중 시작 작업의 이력을 보존하고 추가 생성과 늦은 결과 기록을 막는다")
	void stopsPendingAndClaimedStartJobs(boolean claimed) throws Exception {
		// given
		UUID id = startJob();
		var request = jobRepository.findRequest(id);
		var job = claimed ? jobRepository.claimNextJob(NOW).orElseThrow() : null;
		String previousStatus = claimed ? "PROCESSING" : "PENDING";
		// when
		remove(admin, "ADMIN").andExpect(status().isOk());
		// then
		assertThat(jdbc.queryForObject("select status from notification_jobs where id=?", String.class, bytes(id)))
				.isEqualTo(previousStatus);
		assertThat(jdbc.queryForObject("select json_unquote(json_extract(payload,'$.generationStoppedBy')) from notification_jobs where id=?",
				String.class, bytes(id))).isEqualTo("event-deleted:" + event);
		assertThat(jdbc.queryForObject("select lease_until from notification_jobs where id=?", LocalDateTime.class, bytes(id)))
				.isNull();
		assertThat(jobRepository.claimNextJob(NOW.plusSeconds(120))).isEmpty();
		if (claimed) {
			assertThat(jobRepository.createNotification(job, request, user, NOW)).isFalse();
			jobRepository.completeJob(job, NOW);
			jobRepository.failJob(job, NOW.plusSeconds(10), "TEST-FAILURE");
			assertThat(jdbc.queryForObject("select status from notification_jobs where id=?", String.class, bytes(id)))
					.isEqualTo(previousStatus);
		}
		assertThat(jdbc.queryForObject("select count(*) from notifications where event_id=?", Long.class, bytes(event))).isZero();
		assertThat(jdbc.queryForObject("select count(*) from notification_jobs where event_id=?", Long.class, bytes(event))).isEqualTo(1);
	}

	@Test
	@DisplayName("삭제 후 기존 알림의 내용·읽음·발송 결과와 완료 작업을 보존하고 삭제 안내는 추가하지 않는다")
	void preservesExistingNotificationAndCompletedJob() throws Exception {
		// given
		UUID id = startJob();
		assertThat(jobs.processNextJob()).isTrue();
		assertThat(jobs.processNextDelivery()).isTrue();
		jdbc.update("update notifications set is_read=true where job_id=?", bytes(id));
		var previous = jdbc.queryForMap("select hex(id) as id,title,body,link_url,created_at,is_read,mock_delivery_status,mock_sent_at from notifications where job_id=?", bytes(id));
		// when
		remove(admin, "ADMIN").andExpect(status().isOk());
		// then
		assertThat(jdbc.queryForMap("select hex(id) as id,title,body,link_url,created_at,is_read,mock_delivery_status,mock_sent_at from notifications where job_id=?", bytes(id)))
				.isEqualTo(previous);
		assertThat(jdbc.queryForObject("select status from notification_jobs where id=?", String.class, bytes(id))).isEqualTo("COMPLETED");
		assertThat(jdbc.queryForObject("select count(*) from notification_jobs where event_id=?", Long.class, bytes(event))).isEqualTo(1);
	}

	@Test
	@DisplayName("알림 중지 이후 실패하면 이벤트 삭제와 시작 작업 중지를 함께 롤백한다")
	void rollsBackDeletionAndNotificationStopTogether() {
		// given
		UUID id = startJob();
		doAnswer(invocation -> {
			invocation.callRealMethod();
			throw new IllegalStateException("알림 중지 이후 테스트 오류");
		}).when(notifications).stopForDeletedEvent(event);
		// when / then
		assertThatThrownBy(() -> deletions.delete(event, admin)).isInstanceOf(IllegalStateException.class);
		assertThat(deletedAt()).isNull();
		assertThat(jdbc.queryForObject("select json_unquote(json_extract(payload,'$.generationStoppedBy')) from notification_jobs where id=?",
				String.class, bytes(id))).isNull();
		assertThat(jobRepository.claimNextJob(NOW)).isPresent();
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("알림 생성·삭제가 실제 잠금에 경합해도 먼저 생성된 알림을 보존하고 삭제 이후 추가 생성을 막는다")
	void serializesConcurrentGenerationAndDeletion(boolean deletionFirst) throws Exception {
		// given
		additionalRecipient = user("USER");
		UUID id = startJob(List.of(user, additionalRecipient));
		var request = jobRepository.findRequest(id);
		var job = jobRepository.claimNextJob(NOW).orElseThrow();
		assertThat(jobRepository.createNotification(job, request, user, NOW)).isTrue();
		assertThat(jobs.processNextDelivery()).isTrue();
		jdbc.update("update notifications set is_read=true where job_id=?", bytes(id));
		var previous = jdbc.queryForMap("select hex(id) as id,title,body,link_url,created_at,is_read,mock_delivery_status,mock_sent_at from notifications where job_id=?", bytes(id));
		CountDownLatch held = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		if (deletionFirst) {
			doAnswer(invocation -> {
				invocation.callRealMethod();
				held.countDown();
				if (!release.await(20, TimeUnit.SECONDS)) { throw new IllegalStateException("삭제 커밋 대기 시간 초과"); }
				return null;
			}).when(notifications).stopForDeletedEvent(event);
		} else {
			// 실제 생성 트랜잭션의 작업 잠금 이후, FK를 확인하는 INSERT 전에 잠시 멈춘다.
			doAnswer(invocation -> {
				held.countDown();
				if (!release.await(20, TimeUnit.SECONDS)) { throw new IllegalStateException("알림 INSERT 대기 시간 초과"); }
				return invocation.callRealMethod();
			}).when(jdbc).update(argThat((String sql) -> sql.contains("insert into notifications")), any(Object[].class));
		}
		Callable<Boolean> deletion = () -> { deletions.delete(event, admin); return false; };
		Callable<Boolean> generation = () -> jobRepository.createNotification(job, request, additionalRecipient, NOW);
		// when
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(deletionFirst ? deletion : generation);
			java.util.concurrent.Future<Boolean> second;
			try {
				assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
				second = executor.submit(deletionFirst ? generation : deletion);
				awaitDatabaseLockWait();
			} finally {
				release.countDown();
			}
			// then
			assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(!deletionFirst);
			assertThat(second.get(30, TimeUnit.SECONDS)).isFalse();
		}
		assertThat(deletedAt()).isEqualTo(utc(NOW));
		assertThat(jdbc.queryForMap("select hex(id) as id,title,body,link_url,created_at,is_read,mock_delivery_status,mock_sent_at from notifications where job_id=? and user_id=?", bytes(id), bytes(user)))
				.isEqualTo(previous);
		assertThat(jdbc.queryForObject("select count(*) from notifications where job_id=?", Long.class, bytes(id)))
				.isEqualTo(deletionFirst ? 1L : 2L);
		assertThat(jdbc.queryForObject("select json_unquote(json_extract(payload,'$.generationStoppedBy')) from notification_jobs where id=?", String.class, bytes(id)))
				.isEqualTo("event-deleted:" + event);
		assertThat(jobRepository.createNotification(job, request, additionalRecipient, NOW)).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from notification_jobs where event_id=?", Long.class, bytes(event))).isEqualTo(1);
	}

	/** 두 스레드가 실제 InnoDB 행 잠금에 경합한 것을 확인한 뒤 보유 잠금을 해제한다. */
	private void awaitDatabaseLockWait() throws Exception {
		try (var root = DriverManager.getConnection(mysql.getJdbcUrl(), "root", mysql.getPassword());
				var statement = root.prepareStatement("""
					select count(*) from performance_schema.data_lock_waits w
					join performance_schema.data_locks l on l.engine_lock_id = w.blocking_engine_lock_id
					and l.engine = w.engine where l.object_schema = ?
					""")) {
			statement.setString(1, mysql.getDatabaseName());
			await().atMost(Duration.ofSeconds(10)).until(() -> {
				try (var rows = statement.executeQuery()) {
					return rows.next() && rows.getInt(1) > 0;
				}
			});
		}
	}

	private UUID startJob() {
		return startJob(List.of(user));
	}

	private UUID startJob(List<UUID> recipients) {
		return jobs.register(new NotificationJobRequest("deletion-test:" + event, NotificationType.EVENT_START,
				event, null, "이벤트 시작 안내", "잠시 후 시작", "/events/" + event, NOW, recipients));
	}

	private void participation() {
		jdbc.update("insert into event_participants(id,event_id,user_id,created_at,used_ticket_count) values(?,?,?,?,0)",
				bytes(participant), bytes(event), bytes(user), utc(NOW));
		jdbc.update("""
			insert into event_entries(id,participant_id,user_id,requested_ticket_count,deducted_ticket_count,created_at)
			values(?,?,?,0,0,?)
			""", bytes(UUID.randomUUID()), bytes(participant), bytes(user), utc(NOW));
	}

	private ResultActions remove(UUID actor, String role) throws Exception {
		return mvc.perform(delete("/api/v1/admin/events/" + event)
				.header("X-User-ID", actor).header("X-User-Role", role).header("X-User-Membership", "vip"));
	}

	private UUID user(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users(id,name,role,status,membership,created_at,updated_at) values(?,'삭제 테스트',?,'ACTIVE','VIP',?,?)",
				bytes(id), role, utc(NOW), utc(NOW));
		return id;
	}

	private LocalDateTime deletedAt() {
		return jdbc.queryForObject("select deleted_at from events where id=?", LocalDateTime.class, bytes(event));
	}

	private byte[] bytes(UUID id) { return UuidBinary.toBytes(id); }
	private LocalDateTime utc(Instant time) { return LocalDateTime.ofInstant(time, ZoneOffset.UTC); }
}
