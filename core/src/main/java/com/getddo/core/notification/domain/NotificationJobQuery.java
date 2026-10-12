package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.notification.exception.NotificationAdminErrorCode;

/** 관리자 목록의 선택 필터와 생성 시각 범위 [from,to)를 보관한다. */
@Getter
public final class NotificationJobQuery {
	private final PageQuery page;
	private final NotificationJobStatus status;
	private final NotificationType notificationType;
	private final UUID eventId;
	private final Instant from;
	private final Instant to;

	/** 공개 페이지 범위와 기간 순서를 생성 시 검증한다. */
	public NotificationJobQuery(int page, int size, NotificationJobStatus status, NotificationType notificationType, UUID eventId, Instant from, Instant to) {
		if (page < 1 || size < 1 || size > 100 || (from != null && to != null && !from.isBefore(to))) {
			throw new BusinessException(NotificationAdminErrorCode.INVALID_QUERY);
		}
		this.page = new PageQuery(page, size);
		this.status = status;
		this.notificationType = notificationType;
		this.eventId = eventId;
		this.from = from;
		this.to = to;
	}
}
