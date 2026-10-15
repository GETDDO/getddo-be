package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.ticket.domain.TicketGrade;

/**
 * 미션 완료로 지급한 응모권 결과. 값은 응모권 지급 서비스가 확정한 것을 그대로 담는다.
 */
@Getter
public final class MissionRewardReceipt {

	/** 이 보상을 청구한 보상 청구 ID. 완료 보상은 사용자·미션당 한 번이라 청구와 일대일이다. */
	private final UUID claimId;
	private final long ticketCount;
	private final TicketGrade grade;
	private final Instant grantedAt;
	private final Instant expiresAt;

	public MissionRewardReceipt(UUID claimId, long ticketCount, TicketGrade grade, Instant grantedAt,
			Instant expiresAt) {
		this.claimId = Objects.requireNonNull(claimId, "claimId");
		this.ticketCount = ticketCount;
		this.grade = Objects.requireNonNull(grade, "grade");
		this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
	}
}
