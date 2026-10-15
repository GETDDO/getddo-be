package com.getddo.api.entry.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.entry.domain.EntryStatistics;

/**
 * 이벤트 응모 현황(E03)의 공개 필드다.
 *
 * @param participantCount 접수 완료된 응모자의 중복 제거 수. 추가 응모 건수나 추첨 후보 수와 다르다
 * @param totalSpentTicketCount 접수 완료 건의 차감 합계. 반환을 빼지 않는다
 * @param mySpentTicketCount 요청 사용자의 차감 합계. 응모하지 않았으면 0
 * @param serverTime 서버 시각
 */
@Builder
public record EntryStatisticsResponse(
		UUID eventId,
		long participantCount,
		long totalSpentTicketCount,
		long mySpentTicketCount,
		Instant serverTime) {

	/** 서비스가 돌려준 응모 현황을 응답으로 옮긴다. */
	public static EntryStatisticsResponse from(EntryStatistics statistics) {
		return EntryStatisticsResponse.builder()
				.eventId(statistics.getEventId())
				.participantCount(statistics.getParticipantCount())
				.totalSpentTicketCount(statistics.getTotalSpentTicketCount())
				.mySpentTicketCount(statistics.getMySpentTicketCount())
				.serverTime(statistics.getServerTime())
				.build();
	}
}
