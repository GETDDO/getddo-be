package com.getddo.core.attendance.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

	private static List<LocalDate> september(int... days) {
		List<LocalDate> dates = new ArrayList<>();
		for (int day : days) {
			dates.add(LocalDate.of(2026, 9, day));
		}
		return dates;
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
	@DisplayName("마지막 출석일로 끝나는 연속 구간의 길이를 연속 일수로 하고 하루라도 빠졌으면 그 뒤부터 다시 센다")
	void countsRunEndingAtLastAttendance() {
		// given
		AttendanceStreak started = startedOn("2026-09-01");
		// when
		AttendanceStreak twoDays = started.recalculate(september(1, 2));
		AttendanceStreak afterGap = started.recalculate(september(1, 2, 4));
		AttendanceStreak afterGapRun = started.recalculate(september(1, 2, 4, 5, 6));
		// then
		assertThat(twoDays.getConsecutiveDays()).isEqualTo(2);
		assertThat(afterGap.getConsecutiveDays()).isEqualTo(1);
		assertThat(afterGap.getLastAttendanceDate()).isEqualTo(LocalDate.parse("2026-09-04"));
		assertThat(afterGapRun.getConsecutiveDays()).isEqualTo(3);
		assertThat(started.getConsecutiveDays()).isEqualTo(1);
	}

	@Test
	@DisplayName("31일 동안 매일 출석하면 실제 연속 일수 31을 센다")
	void countsThirtyOneDays() {
		// given
		AttendanceStreak streak = startedOn("2026-10-01");
		List<LocalDate> dates = new ArrayList<>();
		for (int day = 1; day <= 31; day++) {
			dates.add(LocalDate.of(2026, 10, day));
		}
		// when
		AttendanceStreak result = streak.recalculate(dates);
		// then
		assertThat(result.getConsecutiveDays()).isEqualTo(31);
		assertThat(result.getLastAttendanceDate()).isEqualTo(LocalDate.parse("2026-10-31"));
	}

	@Test
	@DisplayName("건너뛴 중간 날짜가 늦게 저장돼도 앞뒤 구간이 이어져 하나의 연속이 된다")
	void lateMiddleDayJoinsRuns() {
		// given: 1~6일과 8일이 먼저 저장됐고 7일이 나중에 들어왔다
		AttendanceStreak streak = startedOn("2026-09-01");
		// when
		AttendanceStreak beforeLateDay = streak.recalculate(september(1, 2, 3, 4, 5, 6, 8));
		AttendanceStreak afterLateDay = streak.recalculate(september(1, 2, 3, 4, 5, 6, 7, 8));
		// then
		assertThat(beforeLateDay.getConsecutiveDays()).isEqualTo(1);
		assertThat(afterLateDay.getConsecutiveDays()).isEqualTo(8);
		assertThat(afterLateDay.getLastAttendanceDate()).isEqualTo(LocalDate.parse("2026-09-08"));
	}

	@Test
	@DisplayName("계산은 식별자와 정책 묶음을 그대로 두고 새 객체를 돌려준다")
	void keepsIdentityAndPolicySet() {
		// given
		UUID id = UUID.randomUUID();
		AttendanceStreak stored = new AttendanceStreak(id, USER_ID, POLICY_SET_ID, LocalDate.parse("2026-09-01"), 1,
				LocalDate.parse("2026-09-01"));
		// when
		AttendanceStreak result = stored.recalculate(september(1, 2));
		// then
		assertThat(result).isNotSameAs(stored);
		assertThat(result.getId()).isEqualTo(id);
		assertThat(result.getPolicySetId()).isEqualTo(POLICY_SET_ID);
		assertThat(stored.getConsecutiveDays()).isEqualTo(1);
	}

	@Test
	@DisplayName("출석 날짜가 없거나 다른 달의 날짜가 섞여 있으면 계산할 수 없다")
	void rejectsEmptyOrOtherMonth() {
		// given
		AttendanceStreak september = startedOn("2026-09-30");
		// when
		// then
		assertThatIllegalArgumentException().isThrownBy(() -> september.recalculate(List.of()));
		assertThatIllegalArgumentException().isThrownBy(() -> september.recalculate(
				List.of(LocalDate.parse("2026-09-30"), LocalDate.parse("2026-10-01"))));
	}
}
