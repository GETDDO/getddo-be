package com.getddo.core.attendance.domain;

/** 출석 보상 종류. {@code attendance_reward_claims.reward_type} ENUM과 같은 값을 가진다. */
public enum AttendanceRewardType {
	DAILY,
	/** 같은 달 연속 출석 단계 달성 보상. */
	STREAK
}
