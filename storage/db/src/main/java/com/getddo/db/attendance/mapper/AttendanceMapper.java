package com.getddo.db.attendance.mapper;

import com.getddo.core.attendance.domain.Attendance;
import com.getddo.core.attendance.domain.AttendanceRewardClaim;
import com.getddo.core.attendance.domain.AttendanceStreak;
import com.getddo.db.attendance.entity.AttendanceEntity;
import com.getddo.db.attendance.entity.AttendanceRewardClaimEntity;
import com.getddo.db.attendance.entity.AttendanceStreakEntity;

/** 출석 Entity와 도메인 객체 변환. */
public final class AttendanceMapper {

	private AttendanceMapper() {
	}

	public static AttendanceEntity toEntity(Attendance attendance) {
		return new AttendanceEntity(attendance.getUserId(), attendance.getAttendanceDate());
	}

	public static Attendance toDomain(AttendanceEntity entity) {
		return new Attendance(entity.getId(), entity.getUserId(), entity.getAttendanceDate(), entity.getCreatedAt());
	}

	public static AttendanceStreakEntity toEntity(AttendanceStreak streak) {
		return new AttendanceStreakEntity(streak.getUserId(), streak.getPolicySetId(), streak.getStreakMonth(),
				streak.getConsecutiveDays(), streak.getLastAttendanceDate());
	}

	public static AttendanceStreak toDomain(AttendanceStreakEntity entity) {
		return new AttendanceStreak(entity.getId(), entity.getUserId(), entity.getPolicySetId(),
				entity.getStreakMonth(), entity.getConsecutiveDays(), entity.getLastAttendanceDate());
	}

	public static AttendanceRewardClaimEntity toEntity(AttendanceRewardClaim claim) {
		return AttendanceRewardClaimEntity.builder()
				.userId(claim.getUserId())
				.attendanceId(claim.getAttendanceId())
				.rewardType(claim.getRewardType())
				.rewardPolicyId(claim.getRewardPolicyId())
				.attendanceStreakPolicyId(claim.getStreakPolicyId())
				.rewardDate(claim.getRewardDate())
				.milestoneDays(claim.getMilestoneDays())
				.sourceKey(claim.getSourceKey())
				.ticketCount(claim.getTicketCount())
				.build();
	}

	public static AttendanceRewardClaim toDomain(AttendanceRewardClaimEntity entity) {
		return new AttendanceRewardClaim(entity.getId(), entity.getUserId(), entity.getAttendanceId(),
				entity.getRewardType(), entity.getRewardPolicyId(), entity.getAttendanceStreakPolicyId(),
				entity.getRewardDate(), entity.getMilestoneDays(), entity.getSourceKey(), entity.getTicketCount());
	}
}
