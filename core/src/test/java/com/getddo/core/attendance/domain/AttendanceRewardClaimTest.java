package com.getddo.core.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AttendanceRewardClaimTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final Attendance SAVED = new Attendance(UUID.randomUUID(), USER_ID, LocalDate.parse("2026-09-07"),
			Instant.parse("2026-09-07T00:00:00Z"));

	@Test
	@DisplayName("일일 보상 청구는 출석 날짜를 중복 방지 키로 쓰고 일일 정책을 가리킨다")
	void dailyClaim() {
		// given
		DailyRewardPolicy policy = new DailyRewardPolicy(UUID.randomUUID(), 1);
		// when
		AttendanceRewardClaim claim = AttendanceRewardClaim.daily(SAVED, policy);
		// then
		assertThat(claim.getRewardType()).isEqualTo(AttendanceRewardType.DAILY);
		assertThat(claim.getSourceKey()).isEqualTo("2026-09-07");
		assertThat(claim.getRewardPolicyId()).isEqualTo(policy.getId());
		assertThat(claim.getStreakPolicyId()).isNull();
		assertThat(claim.getMilestoneDays()).isNull();
		assertThat(claim.getTicketCount()).isEqualTo(1);
		assertThat(claim.getAttendanceId()).isEqualTo(SAVED.getId());
		assertThat(claim.getRewardDate()).isEqualTo(SAVED.getAttendanceDate());
	}

	@Test
	@DisplayName("단계 보상 청구는 기준월과 단계 일수를 중복 방지 키로 써서 같은 달 같은 단계를 한 번만 청구하게 한다")
	void streakClaim() {
		// given
		StreakMilestone seven = new StreakMilestone(UUID.randomUUID(), 7, 1);
		// when
		AttendanceRewardClaim claim = AttendanceRewardClaim.streak(SAVED, seven);
		// then
		assertThat(claim.getRewardType()).isEqualTo(AttendanceRewardType.STREAK);
		assertThat(claim.getSourceKey()).isEqualTo("2026-09:7");
		assertThat(claim.getStreakPolicyId()).isEqualTo(seven.getId());
		assertThat(claim.getRewardPolicyId()).isNull();
		assertThat(claim.getMilestoneDays()).isEqualTo(7);
		assertThat(AttendanceRewardClaim.streakSourceKey(LocalDate.parse("2026-09-30"), 7))
				.isEqualTo(claim.getSourceKey());
		assertThat(AttendanceRewardClaim.streakSourceKey(LocalDate.parse("2026-10-07"), 7)).isEqualTo("2026-10:7");
	}

	@Test
	@DisplayName("저장 전 출석으로는 청구를 만들 수 없다")
	void requiresSavedAttendance() {
		// given
		Attendance unsaved = Attendance.create(USER_ID, LocalDate.parse("2026-09-07"));
		// when
		// then
		assertThatNullPointerException().isThrownBy(() ->
				AttendanceRewardClaim.daily(unsaved, new DailyRewardPolicy(UUID.randomUUID(), 1)));
	}
}
