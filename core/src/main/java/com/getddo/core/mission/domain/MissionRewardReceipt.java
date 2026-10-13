package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.Objects;

import lombok.Getter;

import com.getddo.core.ticket.domain.TicketGrade;

/**
 * 미션 완료로 지급한 응모권 결과. 값은 응모권 지급 서비스가 확정한 것을 그대로 담는다.
 */
@Getter
public final class MissionRewardReceipt {

	private final long ticketCount;
	private final TicketGrade grade;
	private final Instant grantedAt;
	private final Instant expiresAt;

	public MissionRewardReceipt(long ticketCount, TicketGrade grade, Instant grantedAt, Instant expiresAt) {
		this.ticketCount = ticketCount;
		this.grade = Objects.requireNonNull(grade, "grade");
		this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
	}
}
