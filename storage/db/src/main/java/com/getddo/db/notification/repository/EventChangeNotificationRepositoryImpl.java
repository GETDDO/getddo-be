package com.getddo.db.notification.repository;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.getddo.core.notification.domain.EventNotificationChange;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.exception.NotificationProcessingErrorCode;
import com.getddo.core.notification.exception.NotificationProcessingException;
import com.getddo.core.notification.repository.EventChangeNotificationRepository;
import com.getddo.db.common.util.UuidBinary;

/** Service 트랜잭션에서 작업을 멈춘 후 확정된 시작 알림 수신자를 조회한다. */
@Repository
@RequiredArgsConstructor
public class EventChangeNotificationRepositoryImpl implements EventChangeNotificationRepository {
	private final JdbcTemplate jdbc;

	@Override
	public void lockEvent(UUID eventId) {
		List<UUID> events = jdbc.query("select id from events where id = ? for update",
				(row, index) -> UuidBinary.fromBytes(row.getBytes("id")), UuidBinary.toBytes(eventId));
		if (events.isEmpty()) {
			throw new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);
		}
	}

	@Override
	public Optional<UUID> findChangeJobId(String occurrenceKey) {
		return jdbc.query("select id from notification_jobs where occurrence_key = ? for update",
				(row, index) -> UuidBinary.fromBytes(row.getBytes("id")), occurrenceKey).stream().findFirst();
	}

	@Override
	public boolean matchesSavedChange(EventNotificationChange change) {
		boolean canceled = change.getType() == NotificationType.EVENT_CANCELED;
		return Boolean.TRUE.equals(jdbc.queryForObject("""
			select title, status, starts_at, deleted_at from events where id = ? for share
			""", (row, index) -> change.getEventTitle().equals(row.getString("title"))
				&& row.getObject("deleted_at") == null
				&& (canceled ? "CANCELED".equals(row.getString("status"))
						: "SCHEDULED".equals(row.getString("status"))
						&& change.getStartsAt().equals(row.getObject("starts_at", LocalDateTime.class)
								.toInstant(ZoneOffset.UTC))), UuidBinary.toBytes(change.getEventId())));
	}

	@Override
	public void stopStartJobs(UUID eventId, String occurrenceKey) {
		// 성공·실패 상태를 덮어쓰지 않고 실행 중지 근거를 별도로 보존한다.
		jdbc.update("""
			update notification_jobs
			set payload = json_set(payload, '$.generationStoppedBy', ?), lease_until = null
			where event_id = ? and notification_type = 'EVENT_START'
			and json_extract(payload, '$.generationStoppedBy') is null
			""", occurrenceKey, UuidBinary.toBytes(eventId));
	}

	@Override
	public List<UUID> findStartRecipientIds(UUID eventId) {
		// 작업 잠금을 획득한 뒤 현재 읽기로 조회하므로 먼저 완료된 사용자별 생성을 놓치지 않는다.
		return jdbc.query("""
			select n.user_id from notifications n join notification_jobs j on j.id = n.job_id
			where j.event_id = ? and j.notification_type = 'EVENT_START'
			order by n.user_id for share of n
			""", (row, index) -> UuidBinary.fromBytes(row.getBytes("user_id")), UuidBinary.toBytes(eventId))
				.stream().distinct().toList();
	}

	@Override
	public void linkSourceJob(UUID jobId, UUID eventId) {
		List<UUID> sources = jdbc.query("""
			select id from notification_jobs where event_id = ? and notification_type = 'EVENT_START'
			order by created_at desc, id desc limit 1 for share
			""", (row, index) -> UuidBinary.fromBytes(row.getBytes("id")), UuidBinary.toBytes(eventId));
		jdbc.update("update notification_jobs set source_job_id = ? where id = ?",
				sources.isEmpty() ? null : UuidBinary.toBytes(sources.getFirst()), UuidBinary.toBytes(jobId));
	}
}
