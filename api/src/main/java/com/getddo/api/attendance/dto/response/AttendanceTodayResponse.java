package com.getddo.api.attendance.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.getddo.core.attendance.domain.AttendanceToday;

/**
 * AT01 오늘 출석 현황 응답이다.
 *
 * @param attendanceDate 오늘의 KST 업무일
 * @param attended 오늘 출석했는지
 * @param consecutiveDays 오늘 기준으로 이어지고 있는 같은 달의 연속 출석 일수
 * @param dailyRewardTicketCount 일일 출석 보상 수량
 * @param milestones 이번 달 연속 출석 단계, 단계 일수 오름차순
 * @param nextResetAt 다음 출석 기준일이 시작되는 시각(다음 KST 자정)
 * @param serverTime 서버 시각
 */
public record AttendanceTodayResponse(LocalDate attendanceDate, boolean attended, int consecutiveDays,
		int dailyRewardTicketCount, List<AttendanceMilestoneResponse> milestones, Instant nextResetAt,
		Instant serverTime) {

	/**
	 * 오늘 출석 현황을 응답으로 옮긴다.
	 *
	 * @param today 서비스가 반환한 오늘 출석 현황
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static AttendanceTodayResponse from(AttendanceToday today) {
		return new AttendanceTodayResponse(today.getAttendanceDate(), today.isAttended(),
				today.getConsecutiveDays(), today.getDailyRewardTicketCount(),
				today.getMilestones().stream().map(AttendanceMilestoneResponse::from).toList(),
				today.getNextResetAt(), today.getServerTime());
	}
}
