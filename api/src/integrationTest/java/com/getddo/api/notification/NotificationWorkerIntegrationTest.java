package com.getddo.api.notification;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.api.notification.config.NotificationWorker;
import com.getddo.api.support.ApiIntegrationTest;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.service.NotificationJobService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

@ApiIntegrationTest
@TestPropertySource(properties = {
		"getddo.notification.worker.enabled=true",
		"getddo.notification.worker.poll-delay=20"
})
class NotificationWorkerIntegrationTest {
	@Autowired private NotificationWorker worker;
	@Autowired private NotificationJobService service;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private PlatformTransactionManager transactionManager;

	@Test
	@DisplayName("실제 스케줄러는 업무 커밋 후 알림을 한 번 생성·모의 발송하며 미읽음을 유지한다")
	void schedulerProcessesOnlyCommittedJobs() {
		// given
		UUID userId = UUID.randomUUID();
		Instant now = Instant.now();
		LocalDateTime timestamp = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
		jdbc.update("""
			insert into users (id, name, role, status, membership, created_at, updated_at)
			values (?, '알림 워커', 'USER', 'ACTIVE', 'VIP', ?, ?)
			""", bytes(userId), timestamp, timestamp);
		NotificationJobRequest request = new NotificationJobRequest("gd68-worker-test-" + userId,
				NotificationType.RESULT_CHANGED, null, null, "제목", "내용", null, now, List.of(userId));
		UUID jobId = null;
		try {
			// when
			jobId = new TransactionTemplate(transactionManager).execute(status -> {
				UUID id = service.register(request);
				assertThat(jdbc.queryForObject("select count(*) from notifications where job_id = ?",
						Long.class, bytes(id))).isZero();
				return id;
			});

			// then
			assertThat(worker).isNotNull();
			UUID committedJob = jobId;
			await().atMost(10, SECONDS).untilAsserted(() -> {
				assertThat(jdbc.queryForObject("select status from notification_jobs where id = ?",
						String.class, bytes(committedJob))).isEqualTo("COMPLETED");
				assertThat(jdbc.queryForObject("""
					select count(*) from notifications where job_id = ?
					and is_read = false and mock_delivery_status = 'SENT' and delivery_attempt_count = 1
					""", Long.class, bytes(committedJob))).isEqualTo(1);
			});
		} finally {
			if (jobId != null) {
				jdbc.update("delete from notifications where job_id = ?", bytes(jobId));
				jdbc.update("delete from notification_jobs where id = ?", bytes(jobId));
			}
			jdbc.update("delete from users where id = ?", bytes(userId));
		}
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}
}
