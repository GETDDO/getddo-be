package com.getddo.core.entry.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 이벤트 응모 현황(E03). 모든 사용자에게 같은 이벤트 합계와, 요청 사용자 본인의 차감 수량을 함께 보여 준다.
 */
@Getter
public final class EntryStatistics {

	private final UUID eventId;
	/** 접수 완료된 응모자의 중복 제거 수. 추가 응모 건수나 추첨 후보 수와 다르다. */
	private final long participantCount;
	/** 접수 완료 건의 누적 차감 수량 합계. 반환을 빼지 않는다. */
	private final long totalSpentTicketCount;
	/** 요청 사용자의 누적 차감 수량. 응모하지 않았으면 0이다. */
	private final long mySpentTicketCount;
	private final Instant serverTime;

	public EntryStatistics(UUID eventId, long participantCount, long totalSpentTicketCount, long mySpentTicketCount,
			Instant serverTime) {
		this.eventId = Objects.requireNonNull(eventId, "eventId");
		this.serverTime = Objects.requireNonNull(serverTime, "serverTime");
		if (participantCount < 0 || totalSpentTicketCount < 0 || mySpentTicketCount < 0) {
			throw new IllegalArgumentException("응모 현황 수량은 0 이상이어야 한다.");
		}
		if (mySpentTicketCount > totalSpentTicketCount) {
			throw new IllegalArgumentException("본인 차감 수량은 전체 차감 수량을 넘을 수 없다.");
		}
		this.participantCount = participantCount;
		this.totalSpentTicketCount = totalSpentTicketCount;
		this.mySpentTicketCount = mySpentTicketCount;
	}
}
