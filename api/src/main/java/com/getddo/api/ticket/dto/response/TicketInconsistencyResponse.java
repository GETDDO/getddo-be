package com.getddo.api.ticket.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketInconsistencyReport;
import com.getddo.core.ticket.domain.TicketInconsistencyView;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.common.pagination.PageResult;

/**
 * AO09 어긋난 응모권 한 장의 공개 필드다.
 *
 * @param ticketVersion 응모권의 현재 버전
 * @param latestHistoryVersion 마지막 이력의 버전. 이력이 없으면 0
 * @param ticketStatus 응모권의 현재 상태
 * @param latestHistoryStatus 마지막 이력의 상태. 이력이 없으면 null
 * @param checkedAt 요청 시점에 계산한 확인 시각
 */
public record TicketInconsistencyResponse(UUID ticketId, UUID userId, String userName, TicketGrade grade,
		long ticketVersion, long latestHistoryVersion, TicketStatus ticketStatus, TicketStatus latestHistoryStatus,
		Instant checkedAt) {

	/** 보고서의 각 행을 확인 시각과 함께 응답으로 옮긴다. */
	public static PageResult<TicketInconsistencyResponse> from(TicketInconsistencyReport report) {
		return report.getPage().map(view -> from(view, report.getCheckedAt()));
	}

	private static TicketInconsistencyResponse from(TicketInconsistencyView view, Instant checkedAt) {
		return new TicketInconsistencyResponse(view.getTicketId(), view.getUserId(), view.getUserName(),
				view.getGrade(), view.getTicketVersion(), view.getLatestHistoryVersion(), view.getTicketStatus(),
				view.getLatestHistoryStatus(), checkedAt);
	}
}
