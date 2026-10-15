package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;

import lombok.Getter;

import com.getddo.core.common.pagination.PageResult;

/** 전체 사용자의 어긋난 응모권 한 페이지와 확인 시각(AO09). */
@Getter
public final class TicketInconsistencyReport {

	private final PageResult<TicketInconsistencyView> page;
	private final Instant checkedAt;

	public TicketInconsistencyReport(PageResult<TicketInconsistencyView> page, Instant checkedAt) {
		this.page = Objects.requireNonNull(page, "page");
		this.checkedAt = Objects.requireNonNull(checkedAt, "checkedAt");
	}
}
