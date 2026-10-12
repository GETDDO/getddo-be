package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.notification.exception.NotificationAdminErrorCode;

/** 관리자 목록의 선택 필터와 생성 시각 범위 [from,to)를 보관한다. */
@Getter
public final class NotificationDeliveryQuery {
	private final PageQuery page;
	private final UUID userId;
	private final UUID eventId;
	private final UUID jobId;
	private final MockDeliveryStatus mockDeliveryStatus;
	private final Instant from;
	private final Instant to;

	/** 공개 페이지 범위와 기간 순서를 생성 시 검증한다. */
	public NotificationDeliveryQuery(int page, int size, UUID userId, UUID eventId, UUID jobId, MockDeliveryStatus mockDeliveryStatus, Instant from, Instant to) {
		if (page < 1 || size < 1 || size > 100 || (from != null && to != null && !from.isBefore(to))) {
			throw new BusinessException(NotificationAdminErrorCode.INVALID_QUERY);
		}
		this.page = new PageQuery(page, size);
		this.userId = userId;
		this.eventId = eventId;
		this.jobId = jobId;
		this.mockDeliveryStatus = mockDeliveryStatus;
		this.from = from;
		this.to = to;
	}
}
