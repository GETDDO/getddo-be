package com.getddo.db.attendance.entity;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.core.attendance.domain.AttendanceRewardType;
import com.getddo.db.common.entity.BaseEntity;

/**
 * 출석 보상 청구 Entity. 수정하지 않는다.
 *
 * <p>정책 참조는 보상 종류에 따라 하나만 채운다({@code chk_attendance_claim_shape}). 정책·사용자 참조는 연관관계 없이 UUID로 둔다.</p>
 */
@Getter
@Entity
@Table(name = "attendance_reward_claims")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceRewardClaimEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Column(name = "attendance_id", nullable = false, updatable = false, length = 16)
	private UUID attendanceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "reward_type", nullable = false, updatable = false)
	private AttendanceRewardType rewardType;

	/** DAILY일 때만 있는 {@code reward_policies} ID. */
	@Column(name = "reward_policy_id", updatable = false, length = 16)
	private UUID rewardPolicyId;

	/** STREAK일 때만 있는 {@code attendance_streak_policies} ID. */
	@Column(name = "attendance_streak_policy_id", updatable = false, length = 16)
	private UUID attendanceStreakPolicyId;

	@Column(name = "reward_date", nullable = false, updatable = false)
	private LocalDate rewardDate;

	@Column(name = "milestone_days", updatable = false)
	private Integer milestoneDays;

	@Column(name = "source_key", nullable = false, updatable = false, length = 160)
	private String sourceKey;

	@Column(name = "ticket_count", nullable = false, updatable = false)
	private int ticketCount;

	@Builder
	private AttendanceRewardClaimEntity(UUID userId, UUID attendanceId, AttendanceRewardType rewardType,
			UUID rewardPolicyId, UUID attendanceStreakPolicyId, LocalDate rewardDate, Integer milestoneDays,
			String sourceKey, int ticketCount) {
		this.userId = userId;
		this.attendanceId = attendanceId;
		this.rewardType = rewardType;
		this.rewardPolicyId = rewardPolicyId;
		this.attendanceStreakPolicyId = attendanceStreakPolicyId;
		this.rewardDate = rewardDate;
		this.milestoneDays = milestoneDays;
		this.sourceKey = sourceKey;
		this.ticketCount = ticketCount;
	}
}
