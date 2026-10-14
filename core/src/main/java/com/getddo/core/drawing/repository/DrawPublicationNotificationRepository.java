package com.getddo.core.drawing.repository;

import java.util.List;
import java.util.UUID;

/** 공개 시점의 수신 자격 조회. 응모나 알림 데이터를 변경하지 않는다. */
public interface DrawPublicationNotificationRepository {
	/** 접수된 응모 기록이 있는 이벤트 응모자를 사용자별 중복 없이 조회한다. */
	List<UUID> findParticipants(UUID eventId);
}
