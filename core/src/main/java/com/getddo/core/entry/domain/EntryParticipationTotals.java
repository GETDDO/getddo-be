package com.getddo.core.entry.domain;

import lombok.Getter;

/**
 * 한 이벤트의 응모 현황 합계. 접수 완료된 응모자 수와 그들의 누적 차감 수량이다.
 *
 * <p>차감 수량은 반환을 빼지 않은 누적값이다. 거절된 요청은 응모자를 만들지 않으므로 포함되지 않는다.</p>
 */
@Getter
public final class EntryParticipationTotals {

	private final long participantCount;
	private final long spentTicketCount;

	public EntryParticipationTotals(long participantCount, long spentTicketCount) {
		if (participantCount < 0 || spentTicketCount < 0) {
			throw new IllegalArgumentException("응모 현황 합계는 0 이상이어야 한다.");
		}
		this.participantCount = participantCount;
		this.spentTicketCount = spentTicketCount;
	}
}
