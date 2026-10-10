package com.getddo.db.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.attendance.domain.AttendanceMilestoneStatus;
import com.getddo.core.attendance.domain.AttendanceMonth;
import com.getddo.core.attendance.domain.AttendanceReceipt;
import com.getddo.core.attendance.domain.AttendanceRewardReceipt;
import com.getddo.core.attendance.domain.AttendanceRewardType;
import com.getddo.core.attendance.domain.AttendanceToday;
import com.getddo.core.attendance.exception.AttendanceErrorCode;
import com.getddo.core.attendance.exception.AttendanceException;
import com.getddo.core.attendance.service.AttendanceQueryService;
import com.getddo.core.attendance.service.AttendanceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * GD-128 테스트 방법(출석 전후, KST 월 경계, 단계 수령 시각, 다른 사용자 기록 미혼입)을 실제 MySQL에서 검증한다.
 * 시간은 고정 시계로만 움직이고 실제 시각이나 대기에 의존하지 않는다.
 */
class AttendanceQueryServiceIntegrationTest extends AttendanceIntegrationTestSupport {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Autowired
	private AttendanceService attendanceService;
	@Autowired
	private AttendanceQueryService queryService;

	/** 일일 1장, 7·14·28일 단계 1·3·7장 정책을 2026년 전체에 적용한다. */
	private void seedDefaultPolicies() {
		policies.dailyPolicy(adminId, 1, Instant.parse("2025-12-31T15:00:00Z"), null);
		policies.streakPolicySet(adminId, LocalDate.parse("2026-01-01"), AttendanceSeeds.DEFAULT_MILESTONES);
	}

	private void setKst(String kstDateTime) {
		clock.set(LocalDateTime.parse(kstDateTime).atZone(KST).toInstant());
	}

	private AttendanceReceipt attendAt(UUID user, String kstDateTime) {
		setKst(kstDateTime);
		return attendanceService.attend(user);
	}

	/** 날짜 범위의 매일 정오(KST)에 출석하고 마지막 영수증을 돌려준다. */
	private AttendanceReceipt attendEveryDay(UUID user, LocalDate from, LocalDate toInclusive) {
		AttendanceReceipt last = null;
		for (LocalDate day = from; !day.isAfter(toInclusive); day = day.plusDays(1)) {
			last = attendAt(user, day + "T12:00:00");
		}
		return last;
	}

	@Test
	@DisplayName("출석 전에는 attended=false·연속 0이고 일일 보상 수량, 단계 3개, 다음 KST 자정을 돌려준다")
	void todayBeforeAttending() {
		// given
		seedDefaultPolicies();
		setKst("2026-09-15T12:00:00");
		// when
		AttendanceToday today = queryService.getToday(userId);
		// then
		assertThat(today.getAttendanceDate()).isEqualTo(LocalDate.parse("2026-09-15"));
		assertThat(today.isAttended()).isFalse();
		assertThat(today.getConsecutiveDays()).isZero();
		assertThat(today.getDailyRewardTicketCount()).isEqualTo(1);
		assertThat(today.getNextResetAt()).isEqualTo(Instant.parse("2026-09-15T15:00:00Z"));
		assertThat(today.getServerTime()).isEqualTo(LocalDateTime.parse("2026-09-15T12:00:00").atZone(KST).toInstant());
		assertThat(today.getMilestones()).extracting(AttendanceMilestoneStatus::getMilestoneDays,
				AttendanceMilestoneStatus::getRewardTicketCount, AttendanceMilestoneStatus::isClaimed)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(7, 1, false),
						org.assertj.core.groups.Tuple.tuple(14, 3, false),
						org.assertj.core.groups.Tuple.tuple(28, 7, false));
	}

	@Test
	@DisplayName("출석한 날에는 attended=true이고 연속 1일이다")
	void todayAfterAttending() {
		// given
		seedDefaultPolicies();
		attendAt(userId, "2026-09-15T12:00:00");
		// when
		AttendanceToday today = queryService.getToday(userId);
		// then
		assertThat(today.isAttended()).isTrue();
		assertThat(today.getConsecutiveDays()).isEqualTo(1);
	}

	@Test
	@DisplayName("7일 연속 출석한 날 7일 단계의 claimed=true이고 claimedAt은 그 보상의 응모권 지급 시각이다")
	void sevenDayMilestoneIsClaimedWithGrantTime() {
		// given
		seedDefaultPolicies();
		AttendanceReceipt seventh = attendEveryDay(userId, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-07"));
		AttendanceRewardReceipt streakReward = seventh.getRewards().stream()
				.filter(reward -> reward.getRewardType() == AttendanceRewardType.STREAK).findFirst().orElseThrow();
		// when
		AttendanceToday today = queryService.getToday(userId);
		// then
		assertThat(today.getConsecutiveDays()).isEqualTo(7);
		AttendanceMilestoneStatus seven = today.getMilestones().get(0);
		assertThat(seven.isClaimed()).isTrue();
		assertThat(seven.getClaimedAt()).isEqualTo(streakReward.getGrantedAt());
		assertThat(today.getMilestones().get(1).isClaimed()).isFalse();
		assertThat(today.getMilestones().get(1).getClaimedAt()).isNull();
	}

	@Test
	@DisplayName("어제 출석하고 오늘은 아직이면 연속 일수는 유지되고 하루를 건너뛰면 0이 되지만 받은 단계는 남는다")
	void streakContinuesUntilADayIsSkipped() {
		// given
		seedDefaultPolicies();
		attendEveryDay(userId, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-07"));
		// when / then: 8일 낮, 오늘 아직 미출석
		setKst("2026-09-08T12:00:00");
		AttendanceToday nextDay = queryService.getToday(userId);
		assertThat(nextDay.isAttended()).isFalse();
		assertThat(nextDay.getConsecutiveDays()).isEqualTo(7);
		// when / then: 9일에도 출석하지 않아 끊김
		setKst("2026-09-09T12:00:00");
		AttendanceToday skipped = queryService.getToday(userId);
		assertThat(skipped.getConsecutiveDays()).isZero();
		assertThat(skipped.getMilestones().get(0).isClaimed()).isTrue();
	}

	@Test
	@DisplayName("월이 바뀐 직후에는 지난달 연속이 있어도 연속 0이고 새 달에 적용될 묶음을 쓴다")
	void newMonthStartsFromZero() {
		// given
		seedDefaultPolicies();
		attendAt(userId, "2026-09-30T12:00:00");
		// when
		setKst("2026-10-01T00:30:00");
		AttendanceToday today = queryService.getToday(userId);
		// then
		assertThat(today.getAttendanceDate()).isEqualTo(LocalDate.parse("2026-10-01"));
		assertThat(today.isAttended()).isFalse();
		assertThat(today.getConsecutiveDays()).isZero();
		assertThat(today.getMilestones()).hasSize(3).allMatch(milestone -> !milestone.isClaimed());
	}

	@Test
	@DisplayName("KST 자정 직전과 직후는 서로 다른 출석 기준일이다")
	void kstMidnightBoundary() {
		// given
		seedDefaultPolicies();
		attendAt(userId, "2026-09-15T23:59:59");
		// when / then
		setKst("2026-09-15T23:59:59");
		assertThat(queryService.getToday(userId).isAttended()).isTrue();
		setKst("2026-09-16T00:00:00");
		AttendanceToday afterMidnight = queryService.getToday(userId);
		assertThat(afterMidnight.getAttendanceDate()).isEqualTo(LocalDate.parse("2026-09-16"));
		assertThat(afterMidnight.isAttended()).isFalse();
		assertThat(afterMidnight.getConsecutiveDays()).isEqualTo(1);
	}

	@Test
	@DisplayName("적용할 일일 정책이 없으면 ATTENDANCE-001로 실패한다")
	void todayWithoutPolicyFails() {
		// given
		setKst("2026-09-15T12:00:00");
		// when
		// then
		assertThatThrownBy(() -> queryService.getToday(userId))
				.isInstanceOf(AttendanceException.class)
				.extracting(error -> ((AttendanceException) error).getErrorCode())
				.isEqualTo(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND);
	}

	@Test
	@DisplayName("월별 조회는 그 달에 출석한 날짜만 오름차순으로 돌려주고 받은 단계를 표시한다")
	void monthListsOnlyThatMonthsDates() {
		// given
		seedDefaultPolicies();
		attendAt(userId, "2026-08-31T12:00:00");
		attendEveryDay(userId, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-07"));
		attendAt(userId, "2026-09-10T12:00:00");
		attendAt(userId, "2026-10-01T12:00:00");
		// when
		AttendanceMonth september = queryService.getMonth(userId, YearMonth.of(2026, 9));
		// then
		assertThat(september.getMonth()).isEqualTo(YearMonth.of(2026, 9));
		assertThat(september.getAttendanceDates()).containsExactly(LocalDate.parse("2026-09-01"),
				LocalDate.parse("2026-09-02"), LocalDate.parse("2026-09-03"), LocalDate.parse("2026-09-04"),
				LocalDate.parse("2026-09-05"), LocalDate.parse("2026-09-06"), LocalDate.parse("2026-09-07"),
				LocalDate.parse("2026-09-10"));
		assertThat(september.getMilestones().get(0).isClaimed()).isTrue();
		assertThat(september.getMilestones().get(0).getClaimedAt()).isNotNull();
		assertThat(september.getMilestones().get(1).isClaimed()).isFalse();
	}

	@Test
	@DisplayName("지난달에 받은 단계 보상은 이번 달 조회의 claimed에 섞이지 않고 지난달 조회에만 나타난다")
	void previousMonthClaimsDoNotLeakIntoNextMonth() {
		// given: 8월 1~7일 연속 출석으로 7일 단계를 받고, 9월에는 하루만 출석한다
		seedDefaultPolicies();
		attendEveryDay(userId, LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-07"));
		attendAt(userId, "2026-09-01T12:00:00");
		// when
		AttendanceMonth august = queryService.getMonth(userId, YearMonth.of(2026, 8));
		AttendanceMonth september = queryService.getMonth(userId, YearMonth.of(2026, 9));
		// then
		assertThat(august.getMilestones().get(0).isClaimed()).isTrue();
		assertThat(september.getAttendanceDates()).containsExactly(LocalDate.parse("2026-09-01"));
		assertThat(september.getMilestones()).allMatch(milestone -> !milestone.isClaimed());
	}

	@Test
	@DisplayName("기록이 없는 달과 미래의 달은 오류 없이 빈 날짜와 적용될 묶음의 단계를 claimed=false로 돌려준다")
	void emptyMonthsAreNotErrors() {
		// given
		seedDefaultPolicies();
		attendAt(userId, "2026-09-15T12:00:00");
		// when
		AttendanceMonth past = queryService.getMonth(userId, YearMonth.of(2026, 2));
		AttendanceMonth future = queryService.getMonth(userId, YearMonth.of(2027, 5));
		// then
		assertThat(past.getAttendanceDates()).isEmpty();
		assertThat(past.getMilestones()).hasSize(3).allMatch(milestone -> !milestone.isClaimed());
		assertThat(future.getAttendanceDates()).isEmpty();
		assertThat(future.getMilestones()).hasSize(3).allMatch(milestone -> !milestone.isClaimed());
	}

	@Test
	@DisplayName("적용할 단계 묶음이 시작되기 전의 달은 500이 아니라 단계 목록이 빈 목록이다")
	void monthBeforeAnyPolicySetHasNoMilestones() {
		// given
		seedDefaultPolicies();
		// when
		AttendanceMonth beforePolicies = queryService.getMonth(userId, YearMonth.of(2025, 6));
		// then
		assertThat(beforePolicies.getAttendanceDates()).isEmpty();
		assertThat(beforePolicies.getMilestones()).isEmpty();
	}

	@Test
	@DisplayName("다른 사용자의 출석과 단계 수령은 조회 결과에 섞이지 않는다")
	void otherUsersRecordsAreNotMixedIn() {
		// given
		seedDefaultPolicies();
		UUID other = seeds.user();
		attendEveryDay(other, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-07"));
		attendAt(userId, "2026-09-07T12:00:00");
		// when
		setKst("2026-09-07T13:00:00");
		AttendanceToday today = queryService.getToday(userId);
		AttendanceMonth month = queryService.getMonth(userId, YearMonth.of(2026, 9));
		// then
		assertThat(today.getConsecutiveDays()).isEqualTo(1);
		assertThat(today.getMilestones()).allMatch(milestone -> !milestone.isClaimed());
		assertThat(month.getAttendanceDates()).containsExactly(LocalDate.parse("2026-09-07"));
		assertThat(month.getMilestones()).allMatch(milestone -> !milestone.isClaimed());
	}
}
