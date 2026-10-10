package com.getddo.core.attendance.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.attendance.domain.Attendance;
import com.getddo.core.attendance.domain.AttendanceMilestoneStatus;
import com.getddo.core.attendance.domain.AttendanceMonth;
import com.getddo.core.attendance.domain.AttendanceRewardClaim;
import com.getddo.core.attendance.domain.AttendanceRewardType;
import com.getddo.core.attendance.domain.AttendanceStreak;
import com.getddo.core.attendance.domain.AttendanceToday;
import com.getddo.core.attendance.domain.DailyRewardPolicy;
import com.getddo.core.attendance.domain.StreakMilestone;
import com.getddo.core.attendance.domain.StreakPolicySet;
import com.getddo.core.attendance.exception.AttendanceErrorCode;
import com.getddo.core.attendance.exception.AttendanceException;
import com.getddo.core.attendance.repository.AttendancePolicyRepository;
import com.getddo.core.attendance.repository.AttendanceRepository;
import com.getddo.core.attendance.repository.AttendanceRewardClaimRepository;
import com.getddo.core.attendance.repository.AttendanceStreakRepository;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.service.TicketGrantService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceQueryServiceTest {

	/** KST 2026-10-15 12:00. */
	private static final Instant NOW = Instant.parse("2026-10-15T03:00:00Z");
	/** KST 2026-10-01 00:30. 새 달이 막 시작된 시각이다. */
	private static final Instant MONTH_START = Instant.parse("2026-09-30T15:30:00Z");
	private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
	private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 1);
	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID SET_ID = UUID.randomUUID();
	private static final StreakPolicySet POLICY_SET = new StreakPolicySet(SET_ID, List.of(
			new StreakMilestone(UUID.randomUUID(), 7, 1),
			new StreakMilestone(UUID.randomUUID(), 14, 3),
			new StreakMilestone(UUID.randomUUID(), 28, 7)));

	@Mock
	private AttendanceRepository attendanceRepository;
	@Mock
	private AttendanceStreakRepository streakRepository;
	@Mock
	private AttendanceRewardClaimRepository claimRepository;
	@Mock
	private AttendancePolicyRepository policyRepository;
	@Mock
	private TicketGrantService grantService;

	private AttendanceQueryService serviceAt(Instant now) {
		return new AttendanceQueryService(attendanceRepository, streakRepository, claimRepository, policyRepository,
				grantService, new TimeProvider(Clock.fixed(now, ZoneOffset.UTC)));
	}

	private static AttendanceStreak streak(LocalDate month, int days, LocalDate lastAttendanceDate) {
		return new AttendanceStreak(UUID.randomUUID(), USER_ID, SET_ID, month, days, lastAttendanceDate);
	}

	private static AttendanceRewardClaim streakClaim(UUID claimId, int milestoneDays, LocalDate date) {
		return new AttendanceRewardClaim(claimId, USER_ID, UUID.randomUUID(), AttendanceRewardType.STREAK, null,
				UUID.randomUUID(), date, milestoneDays,
				AttendanceRewardClaim.streakSourceKey(date, milestoneDays), 1);
	}

	/** GrantSource는 값 비교를 구현하지 않으므로 종류와 청구 ID를 직접 비교한다. */
	private static boolean isAttendanceClaim(GrantSource source, UUID claimId) {
		return source != null && source.getType() == GrantSourceType.ATTENDANCE && claimId.equals(source.getClaimId());
	}

	private static void assertPolicyNotFound(Runnable call) {
		assertThatThrownBy(call::run)
				.isInstanceOf(AttendanceException.class)
				.extracting(error -> ((AttendanceException) error).getErrorCode())
				.isEqualTo(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND);
	}

	@Nested
	@DisplayName("getToday")
	class GetToday {

		private void givenPolicies() {
			when(policyRepository.findDailyPolicy(any())).thenReturn(Optional.of(new DailyRewardPolicy(UUID.randomUUID(), 2)));
		}

		@Test
		@DisplayName("오늘 출석했으면 저장된 연속 일수를 돌려주고 일일 보상 수량과 다음 KST 자정 초기화 시각을 담는다")
		void attendedToday() {
			// given
			givenPolicies();
			when(streakRepository.find(USER_ID, OCTOBER)).thenReturn(Optional.of(streak(OCTOBER, 3, TODAY)));
			when(policyRepository.findStreakPolicySetById(SET_ID)).thenReturn(Optional.of(POLICY_SET));
			when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY))
					.thenReturn(Optional.of(new Attendance(UUID.randomUUID(), USER_ID, TODAY, NOW)));
			when(claimRepository.findStreakClaims(USER_ID, YearMonth.of(2026, 10))).thenReturn(List.of());
			// when
			AttendanceToday result = serviceAt(NOW).getToday(USER_ID);
			// then
			assertThat(result.getAttendanceDate()).isEqualTo(TODAY);
			assertThat(result.isAttended()).isTrue();
			assertThat(result.getConsecutiveDays()).isEqualTo(3);
			assertThat(result.getDailyRewardTicketCount()).isEqualTo(2);
			assertThat(result.getNextResetAt()).isEqualTo(Instant.parse("2026-10-15T15:00:00Z"));
			assertThat(result.getServerTime()).isEqualTo(NOW);
			assertThat(result.getMilestones()).extracting(AttendanceMilestoneStatus::getMilestoneDays,
					AttendanceMilestoneStatus::getRewardTicketCount, AttendanceMilestoneStatus::isClaimed)
					.containsExactly(org.assertj.core.groups.Tuple.tuple(7, 1, false),
							org.assertj.core.groups.Tuple.tuple(14, 3, false),
							org.assertj.core.groups.Tuple.tuple(28, 7, false));
		}

		@Test
		@DisplayName("어제 출석하고 오늘은 아직이면 오늘 출석 여부는 false이고 연속 일수는 저장값을 유지한다")
		void attendedYesterdayOnly() {
			// given
			givenPolicies();
			when(streakRepository.find(USER_ID, OCTOBER)).thenReturn(Optional.of(streak(OCTOBER, 5, TODAY.minusDays(1))));
			when(policyRepository.findStreakPolicySetById(SET_ID)).thenReturn(Optional.of(POLICY_SET));
			when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
			when(claimRepository.findStreakClaims(any(), any())).thenReturn(List.of());
			// when
			AttendanceToday result = serviceAt(NOW).getToday(USER_ID);
			// then
			assertThat(result.isAttended()).isFalse();
			assertThat(result.getConsecutiveDays()).isEqualTo(5);
		}

		@Test
		@DisplayName("어제도 출석하지 않았으면 연속 일수는 0이다")
		void missedYesterday() {
			// given
			givenPolicies();
			when(streakRepository.find(USER_ID, OCTOBER)).thenReturn(Optional.of(streak(OCTOBER, 5, TODAY.minusDays(2))));
			when(policyRepository.findStreakPolicySetById(SET_ID)).thenReturn(Optional.of(POLICY_SET));
			when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
			when(claimRepository.findStreakClaims(any(), any())).thenReturn(List.of());
			// when
			AttendanceToday result = serviceAt(NOW).getToday(USER_ID);
			// then
			assertThat(result.getConsecutiveDays()).isZero();
		}

		@Test
		@DisplayName("새 달이 막 시작돼 이번 달 기록이 없으면 연속 일수는 0이고 이번 달에 적용될 단계 묶음을 쓴다")
		void firstMomentOfMonth() {
			// given
			givenPolicies();
			when(streakRepository.find(USER_ID, OCTOBER)).thenReturn(Optional.empty());
			when(policyRepository.findStreakPolicySet(OCTOBER)).thenReturn(Optional.of(POLICY_SET));
			when(attendanceRepository.findByUserIdAndDate(USER_ID, OCTOBER)).thenReturn(Optional.empty());
			when(claimRepository.findStreakClaims(any(), any())).thenReturn(List.of());
			// when
			AttendanceToday result = serviceAt(MONTH_START).getToday(USER_ID);
			// then
			assertThat(result.getAttendanceDate()).isEqualTo(OCTOBER);
			assertThat(result.isAttended()).isFalse();
			assertThat(result.getConsecutiveDays()).isZero();
			assertThat(result.getMilestones()).hasSize(3);
			verify(policyRepository, never()).findStreakPolicySetById(any());
		}

		@Test
		@DisplayName("이번 달 출석 기록이 있으면 그 달에 고정된 단계 묶음을 쓰고 이번 달 적용 묶음은 조회하지 않는다")
		void usesPolicySetPinnedByStreak() {
			// given
			givenPolicies();
			when(streakRepository.find(USER_ID, OCTOBER)).thenReturn(Optional.of(streak(OCTOBER, 1, TODAY)));
			when(policyRepository.findStreakPolicySetById(SET_ID)).thenReturn(Optional.of(POLICY_SET));
			when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
			when(claimRepository.findStreakClaims(any(), any())).thenReturn(List.of());
			// when
			serviceAt(NOW).getToday(USER_ID);
			// then
			verify(policyRepository, never()).findStreakPolicySet(any());
		}

		@Test
		@DisplayName("받은 단계는 claimed=true이고 claimedAt은 응모권 지급 시각이며 받지 않은 단계는 claimed=false·claimedAt=null이다")
		void claimedMilestoneHasGrantTime() {
			// given
			givenPolicies();
			UUID claimId = UUID.randomUUID();
			Instant grantedAt = Instant.parse("2026-10-07T03:00:00Z");
			when(streakRepository.find(USER_ID, OCTOBER)).thenReturn(Optional.of(streak(OCTOBER, 3, TODAY)));
			when(policyRepository.findStreakPolicySetById(SET_ID)).thenReturn(Optional.of(POLICY_SET));
			when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
			when(claimRepository.findStreakClaims(USER_ID, YearMonth.of(2026, 10)))
					.thenReturn(List.of(streakClaim(claimId, 7, LocalDate.of(2026, 10, 7))));
			when(grantService.findGrant(argThat(source -> isAttendanceClaim(source, claimId))))
					.thenReturn(Optional.of(new GrantResult(1, TicketGrade.BRONZE, grantedAt,
							Instant.parse("2026-10-31T15:00:00Z"), true)));
			// when
			AttendanceToday result = serviceAt(NOW).getToday(USER_ID);
			// then
			AttendanceMilestoneStatus seven = result.getMilestones().get(0);
			assertThat(seven.isClaimed()).isTrue();
			assertThat(seven.getClaimedAt()).isEqualTo(grantedAt);
			AttendanceMilestoneStatus fourteen = result.getMilestones().get(1);
			assertThat(fourteen.isClaimed()).isFalse();
			assertThat(fourteen.getClaimedAt()).isNull();
		}

		@Test
		@DisplayName("청구는 있는데 지급 기록을 찾지 못하면 claimed=true이고 claimedAt은 null이다")
		void claimedWithoutGrantRecord() {
			// given
			givenPolicies();
			UUID claimId = UUID.randomUUID();
			when(streakRepository.find(USER_ID, OCTOBER)).thenReturn(Optional.of(streak(OCTOBER, 7, TODAY)));
			when(policyRepository.findStreakPolicySetById(SET_ID)).thenReturn(Optional.of(POLICY_SET));
			when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
			when(claimRepository.findStreakClaims(USER_ID, YearMonth.of(2026, 10)))
					.thenReturn(List.of(streakClaim(claimId, 7, LocalDate.of(2026, 10, 7))));
			when(grantService.findGrant(argThat(source -> isAttendanceClaim(source, claimId))))
					.thenReturn(Optional.empty());
			// when
			AttendanceToday result = serviceAt(NOW).getToday(USER_ID);
			// then
			AttendanceMilestoneStatus seven = result.getMilestones().get(0);
			assertThat(seven.isClaimed()).isTrue();
			assertThat(seven.getClaimedAt()).isNull();
		}

		@Test
		@DisplayName("적용할 일일 정책이 없으면 ATTENDANCE-001로 실패한다")
		void noDailyPolicy() {
			// given
			when(policyRepository.findDailyPolicy(any())).thenReturn(Optional.empty());
			// when
			// then
			assertPolicyNotFound(() -> serviceAt(NOW).getToday(USER_ID));
		}

		@Test
		@DisplayName("이번 달에 적용할 단계 묶음이 없으면 ATTENDANCE-001로 실패한다")
		void noStreakPolicySet() {
			// given
			givenPolicies();
			when(streakRepository.find(USER_ID, OCTOBER)).thenReturn(Optional.empty());
			when(policyRepository.findStreakPolicySet(OCTOBER)).thenReturn(Optional.empty());
			// when
			// then
			assertPolicyNotFound(() -> serviceAt(NOW).getToday(USER_ID));
		}
	}

	@Nested
	@DisplayName("getMonth")
	class GetMonth {

		private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
		private static final LocalDate SEPTEMBER_FIRST = LocalDate.of(2026, 9, 1);

		@Test
		@DisplayName("출석 기록이 있는 달은 출석 날짜와 그 달에 고정된 단계 묶음, 받은 단계를 돌려준다")
		void monthWithRecords() {
			// given
			UUID claimId = UUID.randomUUID();
			Instant grantedAt = Instant.parse("2026-09-07T03:00:00Z");
			List<LocalDate> dates = List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3));
			when(attendanceRepository.findAttendanceDates(USER_ID, SEPTEMBER)).thenReturn(dates);
			when(streakRepository.find(USER_ID, SEPTEMBER_FIRST))
					.thenReturn(Optional.of(streak(SEPTEMBER_FIRST, 3, LocalDate.of(2026, 9, 3))));
			when(policyRepository.findStreakPolicySetById(SET_ID)).thenReturn(Optional.of(POLICY_SET));
			when(claimRepository.findStreakClaims(USER_ID, SEPTEMBER))
					.thenReturn(List.of(streakClaim(claimId, 7, LocalDate.of(2026, 9, 7))));
			when(grantService.findGrant(argThat(source -> isAttendanceClaim(source, claimId))))
					.thenReturn(Optional.of(new GrantResult(1, TicketGrade.BRONZE, grantedAt,
							Instant.parse("2026-09-30T15:00:00Z"), true)));
			// when
			AttendanceMonth result = serviceAt(NOW).getMonth(USER_ID, SEPTEMBER);
			// then
			assertThat(result.getMonth()).isEqualTo(SEPTEMBER);
			assertThat(result.getAttendanceDates()).containsExactlyElementsOf(dates);
			assertThat(result.getServerTime()).isEqualTo(NOW);
			assertThat(result.getMilestones()).extracting(AttendanceMilestoneStatus::getMilestoneDays,
					AttendanceMilestoneStatus::isClaimed)
					.containsExactly(org.assertj.core.groups.Tuple.tuple(7, true),
							org.assertj.core.groups.Tuple.tuple(14, false),
							org.assertj.core.groups.Tuple.tuple(28, false));
			assertThat(result.getMilestones().get(0).getClaimedAt()).isEqualTo(grantedAt);
		}

		@Test
		@DisplayName("기록이 없는 달은 오류가 아니며 날짜는 비고 그 달에 적용될 묶음의 단계를 claimed=false로 돌려준다")
		void monthWithoutRecords() {
			// given
			when(attendanceRepository.findAttendanceDates(USER_ID, YearMonth.of(2026, 12))).thenReturn(List.of());
			when(streakRepository.find(USER_ID, LocalDate.of(2026, 12, 1))).thenReturn(Optional.empty());
			when(policyRepository.findStreakPolicySet(LocalDate.of(2026, 12, 1))).thenReturn(Optional.of(POLICY_SET));
			when(claimRepository.findStreakClaims(USER_ID, YearMonth.of(2026, 12))).thenReturn(List.of());
			// when
			AttendanceMonth result = serviceAt(NOW).getMonth(USER_ID, YearMonth.of(2026, 12));
			// then
			assertThat(result.getAttendanceDates()).isEmpty();
			assertThat(result.getMilestones()).hasSize(3).allMatch(milestone -> !milestone.isClaimed());
		}

		@Test
		@DisplayName("적용할 단계 묶음이 없는 달은 500이 아니라 단계 목록이 빈 목록이다")
		void monthWithoutPolicySet() {
			// given
			when(attendanceRepository.findAttendanceDates(USER_ID, YearMonth.of(2020, 1))).thenReturn(List.of());
			when(streakRepository.find(USER_ID, LocalDate.of(2020, 1, 1))).thenReturn(Optional.empty());
			when(policyRepository.findStreakPolicySet(LocalDate.of(2020, 1, 1))).thenReturn(Optional.empty());
			// when
			AttendanceMonth result = serviceAt(NOW).getMonth(USER_ID, YearMonth.of(2020, 1));
			// then
			assertThat(result.getAttendanceDates()).isEmpty();
			assertThat(result.getMilestones()).isEmpty();
		}
	}
}
