package com.getddo.core.attendance.domain;

import java.util.Objects;
import java.util.UUID;

/** 연속 출석 정책 묶음의 단계 하나. 연속 일수가 {@code milestoneDays}에 도달하면 보상한다. */
public final class StreakMilestone {

	private final UUID id;
	private final int milestoneDays;
	private final int rewardTicketCount;

	/**
	 * @param id                단계 정책 ID({@code attendance_streak_policies.id})
	 * @param milestoneDays     단계 일수(1~28)
	 * @param rewardTicketCount 단계 달성 시 지급 수량. 1 이상
	 */
	public StreakMilestone(UUID id, int milestoneDays, int rewardTicketCount) {
		this.id = Objects.requireNonNull(id, "id");
		this.milestoneDays = milestoneDays;
		this.rewardTicketCount = rewardTicketCount;
	}

	public UUID getId() {
		return id;
	}

	public int getMilestoneDays() {
		return milestoneDays;
	}

	public int getRewardTicketCount() {
		return rewardTicketCount;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof StreakMilestone that)) {
			return false;
		}
		return milestoneDays == that.milestoneDays && rewardTicketCount == that.rewardTicketCount
				&& id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, milestoneDays, rewardTicketCount);
	}

	@Override
	public String toString() {
		return "StreakMilestone[milestoneDays=" + milestoneDays + ", rewardTicketCount=" + rewardTicketCount + "]";
	}
}
