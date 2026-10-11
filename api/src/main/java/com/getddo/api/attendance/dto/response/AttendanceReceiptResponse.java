package com.getddo.api.attendance.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.getddo.core.attendance.domain.AttendanceReceipt;

/**
 * AT02 출석 응답이다. 같은 날 재요청이면 이미 확정된 출석과 보상을 그대로 담는다.
 *
 * @param attendanceId 출석 ID
 * @param attendanceDate 출석한 KST 업무일
 * @param consecutiveDays 같은 달 실제 연속 출석 일수
 * @param rewards 이 출석으로 확정된 보상. 재요청에서 다시 지급했다는 뜻은 아니다
 * @param createdAt 출석 기록 시각
 */
public record AttendanceReceiptResponse(UUID attendanceId, LocalDate attendanceDate, int consecutiveDays,
		List<RewardReceiptResponse> rewards, Instant createdAt) {

	/**
	 * 출석 영수증을 응답으로 옮긴다.
	 *
	 * @param receipt 서비스가 반환한 출석 영수증
	 * @return 공개 필드만 담은 응답 DTO
	 */
	public static AttendanceReceiptResponse from(AttendanceReceipt receipt) {
		return new AttendanceReceiptResponse(receipt.getAttendanceId(), receipt.getAttendanceDate(),
				receipt.getConsecutiveDays(), receipt.getRewards().stream().map(RewardReceiptResponse::from).toList(),
				receipt.getCreatedAt());
	}
}
