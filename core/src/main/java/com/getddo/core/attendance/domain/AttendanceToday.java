package com.getddo.core.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;

/** 오늘(KST 업무일) 기준의 출석 현황. 출석 여부, 유효한 연속 일수, 보상 안내와 다음 초기화 시각을 담는다. */
@Getter
public final class AttendanceToday {

	private final LocalDate attendanceDate;
	private final boolean attended;
	/** 오늘 기준으로 이어지고 있는 같은 달의 연속 출석 일수. 어제까지 이어지지 않았으면 0이다. */
	private final int consecutiveDays;
	private final int dailyRewardTicketCount;
	/** 단계 일수 오름차순. */
	private final List<AttendanceMilestoneStatus> milestones;
	/** 다음 출석 기준일이 시작되는 시각(다음 KST 자정) UTC. */
	private final Instant nextResetAt;
	private final Instant serverTime;

	public AttendanceToday(LocalDate attendanceDate, boolean attended, int consecutiveDays,
			int dailyRewardTicketCount, List<AttendanceMilestoneStatus> milestones, Instant nextResetAt,
			Instant serverTime) {
		this.attendanceDate = attendanceDate;
		this.attended = attended;
		this.consecutiveDays = consecutiveDays;
		this.dailyRewardTicketCount = dailyRewardTicketCount;
		this.milestones = List.copyOf(milestones);
		this.nextResetAt = nextResetAt;
		this.serverTime = serverTime;
	}
}
