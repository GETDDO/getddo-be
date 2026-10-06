package com.getddo.core.attendance.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 연속 출석 정책 묶음의 단계 하나. 연속 일수가 {@code milestoneDays}에 도달하면 보상한다. */
@Getter
public final class StreakMilestone {

	private static final int MIN_MILESTONE_DAYS = 1;
	private static final int MAX_MILESTONE_DAYS = 28;

	/** {@code attendance_streak_policies.id}. */
	private final UUID id;
	/** 단계 일수(1~28). */
	private final int milestoneDays;
	/** 단계 달성 시 지급 수량. 1 이상. */
	private final int rewardTicketCount;

	/**
	 * @throws IllegalArgumentException 단계 일수가 1~28 밖이거나 지급 수량이 1 미만인 경우.
	 *         {@code chk_streak_policy_milestone}·{@code chk_streak_policy_reward}와 같은 범위다
	 */
	public StreakMilestone(UUID id, int milestoneDays, int rewardTicketCount) {
		if (milestoneDays < MIN_MILESTONE_DAYS || milestoneDays > MAX_MILESTONE_DAYS) {
			throw new IllegalArgumentException("연속 출석 단계 일수는 1~28이어야 한다.");
		}
		if (rewardTicketCount < 1) {
			throw new IllegalArgumentException("연속 출석 단계 보상 수량은 1 이상이어야 한다.");
		}
		this.id = Objects.requireNonNull(id, "id");
		this.milestoneDays = milestoneDays;
		this.rewardTicketCount = rewardTicketCount;
	}
}
