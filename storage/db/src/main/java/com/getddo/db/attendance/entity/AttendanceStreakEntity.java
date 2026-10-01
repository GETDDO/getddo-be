package com.getddo.db.attendance.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.db.common.entity.BaseUpdatableEntity;

/** 사용자의 월별 연속 출석 현황 Entity. 연속 일수와 마지막 출석일은 {@link #applyAttendance}로만 바꾼다. */
@Getter
@Entity
@Table(name = "attendance_streaks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AttendanceStreakEntity extends BaseUpdatableEntity {

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	/** 이 달에 적용하는 연속 출석 정책 묶음 ID. 월 중에는 바꾸지 않는다. */
	@Column(name = "policy_set_id", nullable = false, updatable = false, length = 16)
	private UUID policySetId;

	/** KST 기준월의 1일. */
	@Column(name = "streak_month", nullable = false, updatable = false)
	private LocalDate streakMonth;

	@Column(name = "consecutive_days", nullable = false)
	private int consecutiveDays;

	@Column(name = "last_attendance_date", nullable = false)
	private LocalDate lastAttendanceDate;

	/**
	 * 새 출석을 반영한 연속 일수와 마지막 출석일을 기록한다. 잠금을 잡은 뒤에만 호출한다.
	 *
	 * @param consecutiveDays    갱신된 연속 일수
	 * @param lastAttendanceDate 갱신된 마지막 출석 KST 날짜
	 */
	public void applyAttendance(int consecutiveDays, LocalDate lastAttendanceDate) {
		this.consecutiveDays = consecutiveDays;
		this.lastAttendanceDate = lastAttendanceDate;
	}
}
