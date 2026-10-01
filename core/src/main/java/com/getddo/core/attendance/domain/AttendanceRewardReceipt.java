package com.getddo.core.attendance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 출석 보상 한 건의 지급 결과. API 응답의 {@code RewardReceipt}에 대응한다.
 *
 * <p>보상 수량은 1장 이상만 허용하므로 모든 보상은 지급 원장이 있고 {@code grantedAt}·{@code expiresAt}이 항상 있다.</p>
 */
public final class AttendanceRewardReceipt {

	private final UUID claimId;
	private final AttendanceRewardType rewardType;
	private final Integer milestoneDays;
	private final int ticketCount;
	private final Instant grantedAt;
	private final Instant expiresAt;

	/**
	 * @param claimId       출석 보상 청구 ID
	 * @param rewardType    일일 또는 연속 출석 단계 보상
	 * @param milestoneDays 단계 보상이면 단계 일수, 일일 보상이면 null
	 * @param ticketCount   지급 수량
	 * @param grantedAt     지급 시각 UTC
	 * @param expiresAt     지급분 만료 시각 UTC
	 */
	public AttendanceRewardReceipt(UUID claimId, AttendanceRewardType rewardType, Integer milestoneDays,
			int ticketCount, Instant grantedAt, Instant expiresAt) {
		this.claimId = Objects.requireNonNull(claimId, "claimId");
		this.rewardType = Objects.requireNonNull(rewardType, "rewardType");
		this.milestoneDays = milestoneDays;
		this.ticketCount = ticketCount;
		this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
	}

	public UUID getClaimId() {
		return claimId;
	}

	public AttendanceRewardType getRewardType() {
		return rewardType;
	}

	/** 단계 보상이면 단계 일수, 일일 보상이면 null. */
	public Integer getMilestoneDays() {
		return milestoneDays;
	}

	public int getTicketCount() {
		return ticketCount;
	}

	public Instant getGrantedAt() {
		return grantedAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AttendanceRewardReceipt that)) {
			return false;
		}
		return ticketCount == that.ticketCount
				&& claimId.equals(that.claimId)
				&& rewardType == that.rewardType
				&& Objects.equals(milestoneDays, that.milestoneDays)
				&& grantedAt.equals(that.grantedAt)
				&& expiresAt.equals(that.expiresAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(claimId, rewardType, milestoneDays, ticketCount, grantedAt, expiresAt);
	}

	@Override
	public String toString() {
		return "AttendanceRewardReceipt[claimId=" + claimId + ", rewardType=" + rewardType
				+ ", milestoneDays=" + milestoneDays + ", ticketCount=" + ticketCount + "]";
	}
}
