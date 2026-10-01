package com.getddo.core.attendance.domain;

import java.util.Objects;
import java.util.UUID;

/** 출석 시각에 적용하는 일일 출석 보상 정책({@code reward_policies}의 ATTENDANCE 정책). */
public final class DailyRewardPolicy {

	private final UUID id;
	private final int rewardTicketCount;

	/**
	 * @param id                보상 정책 ID
	 * @param rewardTicketCount 일일 지급 수량. 1 이상
	 */
	public DailyRewardPolicy(UUID id, int rewardTicketCount) {
		this.id = Objects.requireNonNull(id, "id");
		this.rewardTicketCount = rewardTicketCount;
	}

	public UUID getId() {
		return id;
	}

	public int getRewardTicketCount() {
		return rewardTicketCount;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DailyRewardPolicy that)) {
			return false;
		}
		return rewardTicketCount == that.rewardTicketCount && id.equals(that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, rewardTicketCount);
	}

	@Override
	public String toString() {
		return "DailyRewardPolicy[id=" + id + ", rewardTicketCount=" + rewardTicketCount + "]";
	}
}
