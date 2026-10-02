package com.getddo.core.attendance.domain;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AttendanceStreakTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID POLICY_SET_ID = UUID.randomUUID();

	private static AttendanceStreak startedOn(String date) {
		return AttendanceStreak.start(USER_ID, POLICY_SET_ID, LocalDate.parse(date));
	}

	@Test
	@DisplayName("그 달 첫 출석은 연속 1일로 시작하고 기준월은 그 달 1일이다")
	void startsAtOne() {
		// given
		// when
		AttendanceStreak streak = startedOn("2026-09-15");
		// then
		assertThat(streak.getConsecutiveDays()).isEqualTo(1);
		assertThat(streak.getStreakMonth()).isEqualTo(LocalDate.parse("2026-09-01"));
		assertThat(streak.getLastAttendanceDate()).isEqualTo(LocalDate.parse("2026-09-15"));
		assertThat(streak.getPolicySetId()).isEqualTo(POLICY_SET_ID);
	}

	@Test
	@DisplayName("전날 출석했으면 1을 더하고, 하루라도 빠졌으면 1로 돌아간다")
	void continuesOrResets() {
		// given
		AttendanceStreak started = startedOn("2026-09-01");
		// when
		AttendanceStreak nextDay = started.attend(LocalDate.parse("2026-09-02"));
		AttendanceStreak afterGap = nextDay.attend(LocalDate.parse("2026-09-04"));
		// then
		assertThat(nextDay.getConsecutiveDays()).isEqualTo(2);
		assertThat(afterGap.getConsecutiveDays()).isEqualTo(1);
		assertThat(afterGap.getLastAttendanceDate()).isEqualTo(LocalDate.parse("2026-09-04"));
		assertThat(started.getConsecutiveDays()).isEqualTo(1);
	}

	@Test
	@DisplayName("31일 동안 매일 출석하면 실제 연속 일수 31을 센다")
	void countsThirtyOneDays() {
		// given
		AttendanceStreak streak = startedOn("2026-10-01");
		// when
		for (int day = 2; day <= 31; day++) {
			streak = streak.attend(LocalDate.of(2026, 10, day));
		}
		// then
		assertThat(streak.getConsecutiveDays()).isEqualTo(31);
	}

	@Test
	@DisplayName("마지막 반영일보다 이르거나 같은 날짜가 늦게 도착하면 연속 일수를 바꾸지 않는다")
	void ignoresOutOfOrderAttendance() {
		// given
		AttendanceStreak streak = startedOn("2026-09-01").attend(LocalDate.parse("2026-09-02"));
		// when
		AttendanceStreak earlier = streak.attend(LocalDate.parse("2026-09-01"));
		AttendanceStreak same = streak.attend(LocalDate.parse("2026-09-02"));
		// then
		assertThat(earlier).isEqualTo(streak);
		assertThat(same).isEqualTo(streak);
	}

	@Test
	@DisplayName("다른 달의 출석은 이 현황에 반영할 수 없다")
	void rejectsOtherMonth() {
		// given
		AttendanceStreak september = startedOn("2026-09-30");
		// when
		// then
		assertThatIllegalArgumentException().isThrownBy(() -> september.attend(LocalDate.parse("2026-10-01")));
	}
}
