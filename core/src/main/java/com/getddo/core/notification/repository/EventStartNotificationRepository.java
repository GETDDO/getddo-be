package com.getddo.core.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.notification.domain.EventStartNotification;

/** 기존 이벤트·사용자 정보를 시작 알림 생성 목적으로만 조회한다. */
public interface EventStartNotificationRepository {
	/** 트랜잭션이 끝날 때까지 이벤트를 잠그고, 시작 전 10분 구간의 미등록 이벤트 하나를 반환한다. */
	Optional<EventStartNotification> findNextDueEvent(Instant now, Instant latestStart);

	/** 현재 활성 일반 사용자 중 최소 멤버십 이상인 수신자 ID를 조회한다. */
	List<UUID> findRecipientIds(MembershipRule minimumMembership);
}
