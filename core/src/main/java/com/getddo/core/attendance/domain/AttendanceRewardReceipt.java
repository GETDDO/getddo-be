package com.getddo.core.attendance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 출석 보상 한 건의 지급 결과. API 응답의 {@code RewardReceipt}에 대응한다.
 *
 * <p>보상 수량은 1장 이상만 허용하므로 모든 보상은 지급 원장이 있고 {@code grantedAt}·{@code expiresAt}이 항상 있다.</p>
 */
@Getter
public final class AttendanceRewardReceipt {

	private final UUID claimId;
	private final AttendanceRewardType rewardType;
	/** 단계 보상이면 단계 일수, 일일 보상이면 null. */
	private final Integer milestoneDays;
	private final int ticketCount;
	private final Instant grantedAt;
	private final Instant expiresAt;

	public AttendanceRewardReceipt(UUID claimId, AttendanceRewardType rewardType, Integer milestoneDays,
			int ticketCount, Instant grantedAt, Instant expiresAt) {
		this.claimId = Objects.requireNonNull(claimId, "claimId");
		this.rewardType = Objects.requireNonNull(rewardType, "rewardType");
		this.milestoneDays = milestoneDays;
		this.ticketCount = ticketCount;
		this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
	}
}
