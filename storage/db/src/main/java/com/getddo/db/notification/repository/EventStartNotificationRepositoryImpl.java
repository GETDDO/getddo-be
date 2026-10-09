package com.getddo.db.notification.repository;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.notification.domain.EventStartNotification;
import com.getddo.core.notification.repository.EventStartNotificationRepository;

/** 이벤트와 사용자는 변경하지 않고, 기존 시작 작업을 조회해 불필요한 재등록을 줄인다. */
@Repository
@RequiredArgsConstructor
public class EventStartNotificationRepositoryImpl implements EventStartNotificationRepository {
	private final JdbcTemplate jdbc;

	/**
	 * 서비스 트랜잭션에서 이벤트 상태·일정을 보호하고 동시 처리자의 재등록 시도를 줄인다.
	 *
	 * <p>NOT EXISTS가 이벤트 행 잠금 획득 전에 읽은 뷰에서는 다른 처리자가 커밋한 작업을
	 * 보지 못해 후보를 다시 반환할 수 있다. 최종 작업 중복 방지는 occurrence_key UNIQUE와
	 * 멱등 등록이 담당한다. 같은 입력은 기존 ID를 반환하고, 다른 입력은 OCCURRENCE_CONFLICT로 거절한다.</p>
	 */
	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<EventStartNotification> findNextDueEvent(Instant now, Instant latestStart) {
		List<EventStartNotification> rows = jdbc.query("""
			select e.id, e.title, e.starts_at, e.membership_rule
			from events e
			where e.status = 'SCHEDULED' and e.deleted_at is null
			and e.starts_at > ? and e.starts_at <= ?
			and not exists (
			 select 1 from notification_jobs j
			 where j.event_id = e.id and j.notification_type = 'EVENT_START')
			order by e.starts_at, e.id limit 1 for update skip locked
			""", (row, index) -> new EventStartNotification(uuid(row.getBytes("id")), row.getString("title"),
				row.getObject("starts_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
				MembershipRule.valueOf(row.getString("membership_rule"))),
				LocalDateTime.ofInstant(now, ZoneOffset.UTC), LocalDateTime.ofInstant(latestStart, ZoneOffset.UTC));
		return rows.stream().findFirst();
	}

	/** ENUM의 저장 순서에 의존하지 않고 우수·VIP·VVIP 등급의 명시적 순위를 비교한다. */
	@Override
	public List<UUID> findRecipientIds(MembershipRule minimumMembership) {
		int minimumRank = switch (minimumMembership) {
			case excellent -> 1;
			case vip -> 2;
			case vvip -> 3;
		};
		return jdbc.query("""
			select id from users where role = 'USER' and status = 'ACTIVE'
			and case membership when 'EXCELLENT' then 1 when 'VIP' then 2 when 'VVIP' then 3 else 0 end >= ?
			order by id
			""", (row, index) -> uuid(row.getBytes("id")), minimumRank);
	}

	/** MySQL BINARY(16) UUID를 기존 저장 방식과 동일하게 복원한다. */
	private static UUID uuid(byte[] value) {
		ByteBuffer buffer = ByteBuffer.wrap(value);
		return new UUID(buffer.getLong(), buffer.getLong());
	}
}
