package com.getddo.api.attendance.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.getddo.core.attendance.domain.AttendanceMonth;

/**
 * AT03 월별 출석 응답이다.
 *
 * @param month 조회한 KST 월
 * @param attendanceDates 출석한 KST 날짜, 오름차순. 기록이 없으면 빈 목록
 * @param milestones 그 달에 적용된 연속 출석 단계. 적용할 정책 묶음이 없으면 빈 목록
 * @param serverTime 서버 시각
 */
public record AttendanceMonthResponse(YearMonth month, List<LocalDate> attendanceDates,
		List<AttendanceMilestoneResponse> milestones, Instant serverTime) {

	/**
	 * 월별 출석 현황을 응답으로 옮긴다.
	 *
	 * @param month 서비스가 반환한 월별 출석 현황
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static AttendanceMonthResponse from(AttendanceMonth month) {
		return new AttendanceMonthResponse(month.getMonth(), month.getAttendanceDates(),
				month.getMilestones().stream().map(AttendanceMilestoneResponse::from).toList(),
				month.getServerTime());
	}
}
