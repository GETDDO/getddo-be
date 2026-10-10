package com.getddo.core.attendance.domain;

import java.time.Instant;

import lombok.Getter;

/** 연속 출석 단계 하나의 보상 수량과 이번 달 수령 여부. 출석 현황·월별 조회의 한 항목이다. */
@Getter
public final class AttendanceMilestoneStatus {

	private final int milestoneDays;
	private final int rewardTicketCount;
	/** 해당 달에 이 단계의 보상을 이미 받았는지. 이후 연속이 끊겨도 받은 기록은 유지된다. */
	private final boolean claimed;
	/** 응모권 지급 시각. 아직 받지 않았으면 null. */
	private final Instant claimedAt;

	/**
	 * @throws IllegalArgumentException 단계 일수가 1~28 밖이거나 수량이 1 미만이거나, 받지 않은 단계에 수령 시각이 있는 경우.
	 *         정책의 유효 범위({@code chk_streak_policy_milestone}·{@code chk_streak_policy_reward})와 같다.
	 *         받았는데 지급 시각을 찾지 못한 경우({@code claimed=true}, {@code claimedAt=null})는 허용한다
	 */
	public AttendanceMilestoneStatus(int milestoneDays, int rewardTicketCount, boolean claimed, Instant claimedAt) {
		if (milestoneDays < 1 || milestoneDays > 28) {
			throw new IllegalArgumentException("연속 출석 단계 일수는 1~28이어야 한다.");
		}
		if (rewardTicketCount < 1) {
			throw new IllegalArgumentException("연속 출석 단계 보상 수량은 1 이상이어야 한다.");
		}
		if (!claimed && claimedAt != null) {
			throw new IllegalArgumentException("받지 않은 단계에는 수령 시각이 있을 수 없다.");
		}
		this.milestoneDays = milestoneDays;
		this.rewardTicketCount = rewardTicketCount;
		this.claimed = claimed;
		this.claimedAt = claimedAt;
	}
}
