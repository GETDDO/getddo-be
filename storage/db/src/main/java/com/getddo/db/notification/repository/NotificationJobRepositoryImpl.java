package com.getddo.db.notification.repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.notification.domain.MockDeliveryStatus;
import com.getddo.core.notification.domain.NotificationDelivery;
import com.getddo.core.notification.domain.NotificationJob;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationJobStatus;
import com.getddo.core.notification.domain.NotificationRetryPolicy;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.exception.NotificationProcessingErrorCode;
import com.getddo.core.notification.exception.NotificationProcessingException;
import com.getddo.core.notification.repository.NotificationJobRepository;

/**
 * 기존 알림 테이블의 선점·저장·재시도를 담당한다.
 *
 * <p>선점에는 MySQL의 행 잠금과 SKIP LOCKED를 사용한다. 오래된 처리자가 새 처리자의 결과를
 * 덮어쓰지 못하도록 선점 차수를 조건에 포함한다. JSON 문구의 이스케이프는 DB가 수행하고,
 * 입력 UUID 배열만 표준 문자열로 전달하므로 별도 직렬화 의존성이나 Mapper가 필요하지 않다.</p>
 */
@Repository
@RequiredArgsConstructor
public class NotificationJobRepositoryImpl implements NotificationJobRepository {
	private final JdbcTemplate jdbc;

	/** 업무 트랜잭션의 롤백과 함께 작업 등록도 취소한다. UNIQUE 충돌은 DB 내부에서 해결한다. */
	@Override
	@Transactional
	public UUID register(NotificationJobRequest request, Instant now) {
		String recipients = request.getRecipientIds().stream().map(id -> "\"" + id + "\"")
				.collect(Collectors.joining(",", "[", "]"));
		UUID target = request.getRecipientIds().size() == 1 ? request.getRecipientIds().getFirst() : null;
		jdbc.update("""
			insert into notification_jobs
			(id, event_id, publication_id, target_user_id, payload, occurrence_key,
			 notification_type, status, scheduled_at, created_at)
			values (?, ?, ?, ?, json_object('version', 1, 'title', ?, 'body', ?, 'linkUrl', ?,
			 'recipientIds', cast(? as json)), ?, ?, ?, ?, ?)
			on duplicate key update id = id
			""", bytes(newId()), bytes(request.getEventId()), bytes(request.getPublicationId()), bytes(target),
				request.getTitle(), request.getBody(), request.getLinkUrl(), recipients,
				request.getOccurrenceKey(), request.getType().name(), NotificationJobStatus.PENDING.name(),
				time(request.getScheduledAt()), time(now));
		// 중복 등록에서 확인한 최신 행을 읽어 호출자의 이전 스냅샷 영향을 피한다.
		UUID id = jdbc.queryForObject("select id from notification_jobs where occurrence_key = ? for share",
				(row, index) -> uuid(row.getBytes("id")), request.getOccurrenceKey());
		if (!findRequest(id, true).equals(request)) {
			throw new NotificationProcessingException(NotificationProcessingErrorCode.OCCURRENCE_CONFLICT);
		}
		return id;
	}

	/** 선점만 짧게 확정한다. 서버 종료 후에는 제한 시간과 차수로 다시 선점할 수 있다. */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<NotificationJob> claimNextJob(Instant now) {
		while (true) {
			List<NotificationJob> rows = jdbc.query("""
				select id, attempt_count from notification_jobs
				where scheduled_at <= ? and (
				 (status = 'PENDING' and (next_attempt_at is null or next_attempt_at <= ?))
				 or (status = 'PROCESSING' and (lease_until is null or lease_until <= ?)))
				order by scheduled_at, id limit 1 for update skip locked
				""", (row, index) -> new NotificationJob(uuid(row.getBytes("id")), row.getInt("attempt_count")),
					time(now), time(now), time(now));
			if (rows.isEmpty()) {
				return Optional.empty();
			}
			NotificationJob previous = rows.getFirst();
			if (previous.getAttemptCount() >= NotificationRetryPolicy.MAX_ATTEMPTS) {
				jdbc.update("""
					update notification_jobs set status = 'FAILED', lease_until = null,
					next_attempt_at = null, last_error = ? where id = ?
					""", NotificationProcessingErrorCode.TEMPORARY_FAILURE.getCode(), bytes(previous.getId()));
				continue;
			}
			NotificationJob job = new NotificationJob(previous.getId(), previous.getAttemptCount() + 1);
			jdbc.update("""
				update notification_jobs set status = 'PROCESSING', attempt_count = ?,
				lease_until = ?, next_attempt_at = null where id = ?
				""", job.getAttemptCount(), time(now.plus(NotificationRetryPolicy.LEASE)), bytes(job.getId()));
			return Optional.of(job);
		}
	}

	/** 저장된 대상을 읽으므로 일부 생성 후 재시도에서도 대상이 바뀌지 않는다. */
	@Override
	public NotificationJobRequest findRequest(UUID jobId) {
		return findRequest(jobId, false);
	}

	/** 등록 입력 비교는 잠금 읽기로 수행해 호출자의 이전 스냅샷에 영향을 받지 않는다. */
	private NotificationJobRequest findRequest(UUID jobId, boolean lockingRead) {
		String lockClause = lockingRead ? " for share" : "";
		List<UUID> recipients = jdbc.query("""
			select recipients.user_id from notification_jobs j
			join json_table(j.payload, '$.recipientIds[*]'
			 columns (user_id varchar(36) path '$' error on error)) recipients
			where j.id = ? order by recipients.user_id
			""" + (lockingRead ? " for share of j" : ""),
				(row, index) -> UUID.fromString(row.getString("user_id")), bytes(jobId));
		return jdbc.queryForObject("""
			select occurrence_key, notification_type, event_id, publication_id, scheduled_at,
			json_extract(payload, '$.version') as payload_version,
			json_type(json_extract(payload, '$.recipientIds')) as recipient_type,
			json_unquote(json_extract(payload, '$.title')) as title,
			json_unquote(json_extract(payload, '$.body')) as body,
			if(json_type(json_extract(payload, '$.linkUrl')) = 'NULL', null,
			 json_unquote(json_extract(payload, '$.linkUrl'))) as link_url
			from notification_jobs where id = ?
			""" + lockClause, (row, index) -> {
				if (row.getInt("payload_version") != 1 || !"ARRAY".equals(row.getString("recipient_type"))) {
					throw new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);
				}
				return new NotificationJobRequest(row.getString("occurrence_key"),
						NotificationType.valueOf(row.getString("notification_type")), uuid(row.getBytes("event_id")),
						uuid(row.getBytes("publication_id")), row.getString("title"), row.getString("body"),
						row.getString("link_url"), instant(row, "scheduled_at"), recipients);
			}, bytes(jobId));
	}

	/** 알림 INSERT와 사용자 진행 위치 저장은 함께 확정한다. 재처리는 기존 알림을 그대로 둔다. */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean createNotification(NotificationJob job, NotificationJobRequest request, UUID userId, Instant now) {
		List<Boolean> owned = jdbc.query("""
			select attempt_count, status, lease_until from notification_jobs where id = ? for update
			""", (row, index) -> row.getInt("attempt_count") == job.getAttemptCount()
				&& NotificationJobStatus.PROCESSING.name().equals(row.getString("status"))
				&& instant(row, "lease_until") != null && instant(row, "lease_until").isAfter(now), bytes(job.getId()));
		if (owned.isEmpty() || !owned.getFirst()) {
			return false;
		}
		jdbc.update("""
			insert into notifications
			(id, job_id, user_id, event_id, title, body, link_url, created_at, is_read,
			 mock_delivery_status, delivery_attempt_count)
			values (?, ?, ?, ?, ?, ?, ?, ?, false, 'PENDING', 0)
			on duplicate key update id = id
			""", bytes(newId()), bytes(job.getId()), bytes(userId), bytes(request.getEventId()),
				request.getTitle(), request.getBody(), request.getLinkUrl(), time(now));
		jdbc.update("update notification_jobs set last_processed_user_id = ?, lease_until = ? where id = ?",
				bytes(userId), time(now.plus(NotificationRetryPolicy.LEASE)), bytes(job.getId()));
		return true;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void completeJob(NotificationJob job, Instant now) {
		jdbc.update("""
			update notification_jobs set status = 'COMPLETED', completed_at = ?,
			lease_until = null, next_attempt_at = null, last_error = null
			where id = ? and status = 'PROCESSING' and attempt_count = ?
			""", time(now), bytes(job.getId()), job.getAttemptCount());
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failJob(NotificationJob job, Instant nextAttemptAt, String errorCode) {
		jdbc.update("""
			update notification_jobs set status = ?, next_attempt_at = ?, lease_until = null,
			last_error = ? where id = ? and status = 'PROCESSING' and attempt_count = ?
			""", (nextAttemptAt == null ? NotificationJobStatus.FAILED : NotificationJobStatus.PENDING).name(),
				time(nextAttemptAt), errorCode, bytes(job.getId()), job.getAttemptCount());
	}

	/** 다음 시도 시각을 발송 선점의 제한 시각으로도 사용한다. 별도 스키마 컬럼은 추가하지 않는다. */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<NotificationDelivery> claimNextDelivery(Instant now) {
		while (true) {
			List<NotificationDelivery> rows = jdbc.query("""
				select id, user_id, title, body, link_url, delivery_attempt_count from notifications
				where (mock_delivery_status = 'PENDING'
				 and (next_delivery_attempt_at is null or next_delivery_attempt_at <= ?))
				or (mock_delivery_status = 'FAILED' and next_delivery_attempt_at <= ?)
				order by created_at, id limit 1 for update skip locked
				""", (row, index) -> new NotificationDelivery(uuid(row.getBytes("id")), uuid(row.getBytes("user_id")),
					row.getString("title"), row.getString("body"), row.getString("link_url"),
					row.getInt("delivery_attempt_count")), time(now), time(now));
			if (rows.isEmpty()) {
				return Optional.empty();
			}
			NotificationDelivery previous = rows.getFirst();
			if (previous.getAttemptCount() >= NotificationRetryPolicy.MAX_ATTEMPTS) {
				jdbc.update("""
					update notifications set mock_delivery_status = 'FAILED', next_delivery_attempt_at = null,
					last_delivery_error = ? where id = ?
					""", NotificationProcessingErrorCode.TEMPORARY_FAILURE.getCode(), bytes(previous.getId()));
				continue;
			}
			NotificationDelivery delivery = new NotificationDelivery(previous.getId(), previous.getUserId(),
					previous.getTitle(), previous.getBody(), previous.getLinkUrl(), previous.getAttemptCount() + 1);
			jdbc.update("""
				update notifications set mock_delivery_status = ?, delivery_attempt_count = ?,
				next_delivery_attempt_at = ? where id = ?
				""", MockDeliveryStatus.PENDING.name(), delivery.getAttemptCount(),
					time(now.plus(NotificationRetryPolicy.LEASE)), bytes(delivery.getId()));
			return Optional.of(delivery);
		}
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void completeDelivery(NotificationDelivery delivery, Instant now) {
		jdbc.update("""
			update notifications set mock_delivery_status = 'SENT', mock_sent_at = ?,
			next_delivery_attempt_at = null, last_delivery_error = null
			where id = ? and mock_delivery_status = 'PENDING' and delivery_attempt_count = ?
			""", time(now), bytes(delivery.getId()), delivery.getAttemptCount());
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failDelivery(NotificationDelivery delivery, Instant nextAttemptAt, String errorCode) {
		jdbc.update("""
			update notifications set mock_delivery_status = 'FAILED', next_delivery_attempt_at = ?,
			last_delivery_error = ?
			where id = ? and mock_delivery_status = 'PENDING' and delivery_attempt_count = ?
			""", time(nextAttemptAt), errorCode, bytes(delivery.getId()), delivery.getAttemptCount());
	}

	/** 기존 BaseEntity와 같은 Hibernate UUID v7 생성기를 JDBC INSERT에도 재사용한다. */
	private static UUID newId() {
		return UuidVersion7Strategy.INSTANCE.generateUuid(null);
	}

	private static byte[] bytes(UUID id) {
		return id == null ? null : ByteBuffer.allocate(16)
				.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}

	private static UUID uuid(byte[] value) {
		if (value == null) {
			return null;
		}
		ByteBuffer buffer = ByteBuffer.wrap(value);
		return new UUID(buffer.getLong(), buffer.getLong());
	}

	/** DATETIME에는 JVM 기본 시간대와 무관하게 UTC의 날짜·시각을 전달한다. */
	private static LocalDateTime time(Instant instant) {
		return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static Instant instant(ResultSet row, String column) throws SQLException {
		LocalDateTime value = row.getObject(column, LocalDateTime.class);
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}
}
