package com.getddo.core.attendance.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 사용자의 월별 연속 출석 현황.
 *
 * <p>연속 일수는 같은 KST 기준월 안에서만 센다. 매월 1일에는 새 현황으로 시작하고, 하루라도 빠지면 1로 돌아간다.
 * 그 달에 처음 적용한 연속 출석 정책 묶음을 월말까지 유지한다.</p>
 */
@Getter
public final class AttendanceStreak {

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID userId;
	/** 이 달에 적용하는 연속 출석 정책 묶음 ID. */
	private final UUID policySetId;
	/** KST 기준월의 1일. */
	private final LocalDate streakMonth;
	/** 마지막 출석일까지 이어진 연속 일수. */
	private final int consecutiveDays;
	/** 마지막으로 반영한 출석 KST 날짜. */
	private final LocalDate lastAttendanceDate;

	public AttendanceStreak(UUID id, UUID userId, UUID policySetId, LocalDate streakMonth, int consecutiveDays,
			LocalDate lastAttendanceDate) {
		this.id = id;
		this.userId = Objects.requireNonNull(userId, "userId");
		this.policySetId = Objects.requireNonNull(policySetId, "policySetId");
		this.streakMonth = Objects.requireNonNull(streakMonth, "streakMonth");
		this.consecutiveDays = consecutiveDays;
		this.lastAttendanceDate = Objects.requireNonNull(lastAttendanceDate, "lastAttendanceDate");
	}

	/** 그 달의 첫 출석으로 연속 1일인 현황을 시작한다. */
	public static AttendanceStreak start(UUID userId, UUID policySetId, LocalDate attendanceDate) {
		return new AttendanceStreak(null, userId, policySetId, attendanceDate.withDayOfMonth(1), 1, attendanceDate);
	}

	/**
	 * 그 달의 출석 날짜로 현황을 다시 계산해 반환한다. 이 객체는 바꾸지 않는다.
	 *
	 * <p>연속 일수는 마지막 출석일로 끝나는 연속 구간의 길이다. 요청이 처리된 순서가 아니라 출석 기록만으로 정하므로,
	 * 자정 전후 요청이 거꾸로 커밋되어도 순서대로 처리했을 때와 같은 결과가 된다.</p>
	 *
	 * @param sortedAttendedDates 그 달의 출석 KST 날짜(오름차순). 비어 있으면 안 된다
	 * @throws IllegalArgumentException 날짜가 없거나 다른 달의 날짜가 있는 경우
	 */
	public AttendanceStreak recalculate(List<LocalDate> sortedAttendedDates) {
		if (sortedAttendedDates.isEmpty()) {
			throw new IllegalArgumentException("출석 날짜가 있어야 연속 현황을 계산할 수 있다.");
		}
		if (sortedAttendedDates.stream().anyMatch(date -> !date.withDayOfMonth(1).equals(streakMonth))) {
			throw new IllegalArgumentException("연속 출석은 같은 달 안에서만 이어진다.");
		}
		List<ConsecutiveRuns.Run> runs = ConsecutiveRuns.of(sortedAttendedDates);
		ConsecutiveRuns.Run last = runs.get(runs.size() - 1);
		LocalDate lastDate = last.start().plusDays(last.length() - 1L);
		return new AttendanceStreak(id, userId, policySetId, streakMonth, last.length(), lastDate);
	}
}
