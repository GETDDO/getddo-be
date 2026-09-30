package com.getddo.db.notification.repository;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.notification.domain.Notification;
import com.getddo.core.notification.domain.NotificationErrorCode;
import com.getddo.core.notification.repository.NotificationRepository;
import com.getddo.db.notification.entity.NotificationEntity;
import com.getddo.db.notification.mapper.NotificationMapper;

/**
 * 알림 영속성 구현이다.
 *
 * <p>사용자 존재·멤버십 확인은 현재 시연용 헤더 처리에 필요한 읽기 전용 조회다.
 * 알림 목록은 불투명 커서로 위치를 전달하고, 실제 정렬·갱신은 DB에서 수행한다.</p>
 */
@Repository
public class NotificationRepositoryImpl implements NotificationRepository {
	private final NotificationJpaRepository notifications;
	private final JdbcTemplate jdbc;

	public NotificationRepositoryImpl(NotificationJpaRepository notifications, JdbcTemplate jdbc) {
		this.notifications = notifications;
		this.jdbc = jdbc;
	}

	/** DB의 등록 사용자 행 존재 여부를 확인한다. */
	@Override
	public boolean userExists(UUID userId) {
		return jdbc.queryForObject("select count(*) from users where id = ?", Long.class, bytes(userId)) > 0;
	}

	/** 선택한 멤버십을 DB 값과 비교하며 사용자 정보를 변경하지 않는다. */
	@Override
	public boolean membershipMatches(UUID userId, String membership) {
		return jdbc.queryForObject("select count(*) from users where id = ? and membership = ?",
				Long.class, bytes(userId), membership) > 0;
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16)
				.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>요청 크기보다 한 건 더 조회해 다음 페이지 유무를 판단한다.
	 * 다음 커서는 반환한 마지막 행으로 만들고, 전체 건수는 커서 위치를 제외한 조건으로 센다.</p>
	 */
	@Override
	public CursorResult<Notification> findMine(UUID userId, String cursor, int size, Boolean isRead) {
		Cursor after = decode(cursor);
		List<NotificationEntity> rows = notifications.findMine(userId, isRead,
				after == null ? null : after.createdAt,
				after == null ? null : after.id, PageRequest.of(0, size + 1));
		boolean hasNext = rows.size() > size;
		List<NotificationEntity> page = hasNext ? rows.subList(0, size) : rows;
		String nextCursor = hasNext ? encode(page.getLast()) : null;
		return new CursorResult<>(page.stream().map(NotificationMapper::toDomain).toList(),
				nextCursor, notifications.countMine(userId, isRead));
	}

	/** 미읽음 갱신이 0건일 때 기존 본인 알림인지 확인한다. */
	@Override
	public boolean belongsTo(UUID notificationId, UUID userId) {
		return notifications.existsByIdAndUserId(notificationId, userId);
	}

	/** 소유자와 미읽음 상태를 조건으로 알림 한 건을 갱신한다. */
	@Override
	public int markRead(UUID notificationId, UUID userId) {
		return notifications.markRead(notificationId, userId);
	}

	/** 본인의 미읽음 알림 전체를 갱신한다. */
	@Override
	public long markAllRead(UUID userId) {
		return notifications.markAllRead(userId);
	}

	/** 응답에 전달할 커서를 URL 안전 Base64로 인코딩한다. */
	private static String encode(NotificationEntity entity) {
		String value = entity.getCreatedAt() + "|" + entity.getId();
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	/** 커서의 시각·ID를 복원하고 형식 오류는 조회 조건 오류로 바꾼다. */
	private static Cursor decode(String encoded) {
		if (encoded == null) {
			return null;
		}
		try {
			String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			String[] parts = value.split("\\|", -1);
			if (parts.length != 2) {
				throw new IllegalArgumentException();
			}
			return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
		} catch (IllegalArgumentException | DateTimeException exception) {
			throw new BusinessException(NotificationErrorCode.INVALID_QUERY);
		}
	}

	private record Cursor(Instant createdAt, UUID id) {
	}
}
