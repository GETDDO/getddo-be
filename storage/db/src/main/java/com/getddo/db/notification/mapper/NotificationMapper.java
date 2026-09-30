package com.getddo.db.notification.mapper;

import com.getddo.core.notification.domain.Notification;
import com.getddo.db.notification.entity.NotificationEntity;

/** 알림 Entity의 조회 결과를 영속성 기술에 의존하지 않는 도메인 값으로 변환한다. */
public final class NotificationMapper {

	private NotificationMapper() {
	}

	/**
	 * 알림함에 필요한 조회 필드만 도메인 값으로 옮긴다. 사용자 조회나 업무 검증은 수행하지 않는다.
	 *
	 * @param entity 저장소에서 조회한 알림 Entity
	 * @return 알림함에 표시할 도메인 값
	 */
	public static Notification toDomain(NotificationEntity entity) {
		return new Notification(entity.getId(), entity.getTitle(), entity.getBody(),
				entity.getCreatedAt(), entity.isRead(), entity.getEventId(), entity.getLinkUrl());
	}
}
