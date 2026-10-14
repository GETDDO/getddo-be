package com.getddo.core.notification.repository;

import java.util.UUID;

import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.notification.domain.Notification;

/** 알림 소유자 범위의 조회·읽음 갱신에 필요한 저장 작업을 정의한다. */
public interface NotificationRepository {
	/**
	 * 본인 알림을 생성 시각·ID 역순으로 조회한다. 조회는 읽음 상태를 바꾸지 않는다.
	 *
	 * @param userId 알림 소유자 ID
	 * @param cursor 이전 응답의 다음 커서. 첫 조회에서는 null
	 * @param size 조회할 최대 건수
	 * @param isRead 읽음 필터. null이면 전체 상태
	 * @return 목록, 다음 커서, 커서 위치와 무관한 필터 전체 건수
	 */
	CursorResult<Notification> findMine(UUID userId, String cursor, int size, Boolean isRead);
	/** 이미 읽은 알림의 재요청을 구별하기 위해 알림 소유권을 확인한다. */
	boolean belongsTo(UUID notificationId, UUID userId);
	/**
	 * 본인의 미읽음 알림 한 건을 원자적으로 갱신한다.
	 *
	 * @param notificationId 읽음 처리할 알림 ID
	 * @param userId 알림 소유자 ID
	 * @return 변경된 행 수. 0은 이미 읽음·부재·타인 소유일 수 있으므로 소유권 확인이 필요
	 */
	int markRead(UUID notificationId, UUID userId);
	/** 본인의 미읽음 알림만 갱신하고 실제 변경 행 수를 반환한다. */
	long markAllRead(UUID userId);
}
