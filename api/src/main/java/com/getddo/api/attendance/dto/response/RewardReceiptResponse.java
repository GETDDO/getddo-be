package com.getddo.api.attendance.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.attendance.domain.AttendanceRewardReceipt;

/**
 * 출석으로 확정된 보상 한 건의 공개 필드다.
 *
 * @param claimId 보상 청구 ID
 * @param ticketCount 지급 수량
 * @param grantedAt 응모권 지급 시각
 * @param expiresAt 지급분의 만료 시각
 */
public record RewardReceiptResponse(UUID claimId, int ticketCount, Instant grantedAt, Instant expiresAt) {

	/**
	 * 보상 영수증을 응답으로 옮긴다.
	 *
	 * @param reward 서비스가 반환한 보상 영수증
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static RewardReceiptResponse from(AttendanceRewardReceipt reward) {
		return new RewardReceiptResponse(reward.getClaimId(), reward.getTicketCount(), reward.getGrantedAt(),
				reward.getExpiresAt());
	}
}
