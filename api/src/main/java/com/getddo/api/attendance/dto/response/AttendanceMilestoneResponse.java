package com.getddo.api.attendance.dto.response;

import java.time.Instant;

import com.getddo.core.attendance.domain.AttendanceMilestoneStatus;

/**
 * 연속 출석 단계 하나의 공개 필드다.
 *
 * @param milestoneDays 단계 일수
 * @param rewardTicketCount 단계 보상 수량
 * @param claimed 해당 달에 이 단계의 보상을 이미 받았는지
 * @param claimedAt 응모권 지급 시각. 받지 않았으면 null
 */
public record AttendanceMilestoneResponse(int milestoneDays, int rewardTicketCount, boolean claimed,
		Instant claimedAt) {

	/**
	 * 단계 현황을 응답으로 옮긴다.
	 *
	 * @param status 서비스가 반환한 단계 현황
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static AttendanceMilestoneResponse from(AttendanceMilestoneStatus status) {
		return new AttendanceMilestoneResponse(status.getMilestoneDays(), status.getRewardTicketCount(),
				status.isClaimed(), status.getClaimedAt());
	}
}
