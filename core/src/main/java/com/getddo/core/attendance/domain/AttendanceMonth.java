package com.getddo.core.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import lombok.Getter;

/** 한 달의 출석 날짜와 그 달에 적용된 연속 출석 단계 현황. 기록이 없는 달은 날짜가 빈 목록이다. */
@Getter
public final class AttendanceMonth {

	private final YearMonth month;
	/** 출석한 KST 날짜, 오름차순. */
	private final List<LocalDate> attendanceDates;
	/** 그 달에 적용된 단계, 단계 일수 오름차순. 적용할 정책 묶음이 없으면 빈 목록이다. */
	private final List<AttendanceMilestoneStatus> milestones;
	private final Instant serverTime;

	public AttendanceMonth(YearMonth month, List<LocalDate> attendanceDates,
			List<AttendanceMilestoneStatus> milestones, Instant serverTime) {
		this.month = month;
		this.attendanceDates = List.copyOf(attendanceDates);
		this.milestones = List.copyOf(milestones);
		this.serverTime = serverTime;
	}
}
