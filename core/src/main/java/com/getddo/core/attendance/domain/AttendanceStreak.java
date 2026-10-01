package com.getddo.core.attendance.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * 사용자의 월별 연속 출석 현황.
 *
 * <p>연속 일수는 같은 KST 기준월 안에서만 센다. 매월 1일에는 새 현황으로 시작하고, 전날 출석하지 않았으면 1로 돌아간다.
 * 그 달에 처음 적용한 연속 출석 정책 묶음을 월말까지 유지한다.</p>
 */
public final class AttendanceStreak {

	private final UUID id;
	private final UUID userId;
	private final UUID policySetId;
	private final LocalDate streakMonth;
	private final int consecutiveDays;
	private final LocalDate lastAttendanceDate;

	/**
	 * @param id                 현황 ID. 저장 전에는 null
	 * @param userId             사용자 ID
	 * @param policySetId        이 달에 적용하는 연속 출석 정책 묶음 ID
	 * @param streakMonth        KST 기준월의 1일
	 * @param consecutiveDays    마지막 출석일까지 이어진 연속 일수
	 * @param lastAttendanceDate 마지막으로 반영한 출석 KST 날짜
	 */
	public AttendanceStreak(UUID id, UUID userId, UUID policySetId, LocalDate streakMonth, int consecutiveDays,
			LocalDate lastAttendanceDate) {
		this.id = id;
		this.userId = Objects.requireNonNull(userId, "userId");
		this.policySetId = Objects.requireNonNull(policySetId, "policySetId");
		this.streakMonth = Objects.requireNonNull(streakMonth, "streakMonth");
		this.consecutiveDays = consecutiveDays;
		this.lastAttendanceDate = Objects.requireNonNull(lastAttendanceDate, "lastAttendanceDate");
	}

	/**
	 * 그 달의 첫 출석으로 현황을 시작한다. 연속 일수는 1이다.
	 *
	 * @param userId         사용자 ID
	 * @param policySetId    이 달에 적용할 정책 묶음 ID
	 * @param attendanceDate 첫 출석 KST 날짜
	 * @return 저장 전 현황
	 */
	public static AttendanceStreak start(UUID userId, UUID policySetId, LocalDate attendanceDate) {
		return new AttendanceStreak(null, userId, policySetId, attendanceDate.withDayOfMonth(1), 1, attendanceDate);
	}

	/**
	 * 같은 달의 새 출석을 반영한 현황을 반환한다. 이 객체는 바꾸지 않는다.
	 *
	 * <p>전날 출석했으면 1을 더하고, 하루라도 빠졌으면 1부터 다시 센다. 자정 전후 요청이 거꾸로 처리되어 마지막 반영일보다
	 * 이르거나 같은 날짜가 오면 연속 일수를 바꾸지 않는다.</p>
	 *
	 * @param attendanceDate 새 출석 KST 날짜. 이 현황과 같은 달이어야 한다
	 * @return 반영 결과
	 * @throws IllegalArgumentException 다른 달의 날짜인 경우
	 */
	public AttendanceStreak attend(LocalDate attendanceDate) {
		if (!attendanceDate.withDayOfMonth(1).equals(streakMonth)) {
			throw new IllegalArgumentException("연속 출석은 같은 달 안에서만 이어진다.");
		}
		if (!attendanceDate.isAfter(lastAttendanceDate)) {
			return this;
		}
		int days = attendanceDate.equals(lastAttendanceDate.plusDays(1)) ? consecutiveDays + 1 : 1;
		return new AttendanceStreak(id, userId, policySetId, streakMonth, days, attendanceDate);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getPolicySetId() {
		return policySetId;
	}

	public LocalDate getStreakMonth() {
		return streakMonth;
	}

	public int getConsecutiveDays() {
		return consecutiveDays;
	}

	public LocalDate getLastAttendanceDate() {
		return lastAttendanceDate;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AttendanceStreak that)) {
			return false;
		}
		return consecutiveDays == that.consecutiveDays && Objects.equals(id, that.id) && userId.equals(that.userId)
				&& policySetId.equals(that.policySetId) && streakMonth.equals(that.streakMonth)
				&& lastAttendanceDate.equals(that.lastAttendanceDate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, userId, policySetId, streakMonth, consecutiveDays, lastAttendanceDate);
	}

	@Override
	public String toString() {
		return "AttendanceStreak[userId=" + userId + ", streakMonth=" + streakMonth + ", consecutiveDays="
				+ consecutiveDays + ", lastAttendanceDate=" + lastAttendanceDate + "]";
	}
}
