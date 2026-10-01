package com.getddo.core.attendance.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 연속 출석 정책 묶음의 단계 하나. 연속 일수가 {@code milestoneDays}에 도달하면 보상한다. */
@Getter
@EqualsAndHashCode
@ToString
public final class StreakMilestone {

	/** {@code attendance_streak_policies.id}. */
	private final UUID id;
	/** 단계 일수(1~28). */
	private final int milestoneDays;
	/** 단계 달성 시 지급 수량. 1 이상. */
	private final int rewardTicketCount;

	public StreakMilestone(UUID id, int milestoneDays, int rewardTicketCount) {
		this.id = Objects.requireNonNull(id, "id");
		this.milestoneDays = milestoneDays;
		this.rewardTicketCount = rewardTicketCount;
	}
}
