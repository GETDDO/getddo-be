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

	public AttendanceMilestoneStatus(int milestoneDays, int rewardTicketCount, boolean claimed, Instant claimedAt) {
		this.milestoneDays = milestoneDays;
		this.rewardTicketCount = rewardTicketCount;
		this.claimed = claimed;
		this.claimedAt = claimedAt;
	}
}
