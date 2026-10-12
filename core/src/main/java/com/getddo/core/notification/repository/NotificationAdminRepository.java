package com.getddo.core.notification.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.notification.domain.AdminNotification;
import com.getddo.core.notification.domain.AdminNotificationJob;
import com.getddo.core.notification.domain.NotificationDeliveryQuery;
import com.getddo.core.notification.domain.NotificationJobQuery;

/** 관리자 조회와 복구 접수만 담당한다. 실제 처리는 기존 워커가 수행한다. */
public interface NotificationAdminRepository {
	PageResult<AdminNotificationJob> findJobs(NotificationJobQuery query);
	Optional<AdminNotificationJob> findJob(UUID id);
	/** GD-70과 동일한 이벤트 → 작업 잠금 순서로 최신 상태를 읽는다. */
	Optional<AdminNotificationJob> lockJob(UUID id);
	PageResult<AdminNotification> findDeliveries(NotificationDeliveryQuery query);
	Optional<AdminNotification> lockDelivery(UUID id);
	Optional<AdminNotification> findDelivery(UUID id);
	/** 누적 차수를 유지하고 새 재시도 구간을 시작한다. */
	boolean retryJob(UUID id, Instant now);
	boolean retryDelivery(UUID id, Instant now);
}
