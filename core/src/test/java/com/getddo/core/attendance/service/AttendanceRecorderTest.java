package com.getddo.core.attendance.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.attendance.domain.Attendance;
import com.getddo.core.attendance.domain.AttendanceReceipt;
import com.getddo.core.attendance.domain.AttendanceRewardClaim;
import com.getddo.core.attendance.domain.AttendanceRewardReceipt;
import com.getddo.core.attendance.domain.AttendanceRewardType;
import com.getddo.core.attendance.domain.AttendanceStreak;
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
import com.getddo.core.ticket.domain.GrantCommand;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.service.TicketGrantService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceRecorderTest {

	/** 2026-09-07 12:00 KST. */
	private static final Instant NOW = Instant.parse("2026-09-07T03:00:00.123456Z");
	private static final LocalDate TODAY = LocalDate.parse("2026-09-07");
	private static final LocalDate SEPTEMBER = LocalDate.parse("2026-09-01");
	private static final UUID USER_ID = UUID.randomUUID();
	private static final DailyRewardPolicy DAILY = new DailyRewardPolicy(UUID.randomUUID(), 1);
	private static final StreakMilestone SEVEN = new StreakMilestone(UUID.randomUUID(), 7, 1);
	private static final StreakPolicySet SET = new StreakPolicySet(UUID.randomUUID(), List.of(SEVEN,
			new StreakMilestone(UUID.randomUUID(), 14, 3), new StreakMilestone(UUID.randomUUID(), 28, 7)));

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

	private AttendanceRecorder recorder;

	@BeforeEach
	void setUp() {
		// 출석일은 넘겨받은 요청 시각으로만 정한다. 시계를 다른 날로 두어 시계를 읽지 않는지 함께 확인한다.
		recorder = new AttendanceRecorder(attendanceRepository, streakRepository, claimRepository, policyRepository,
				grantService, new TimeProvider(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
	}

	private static Attendance saved(LocalDate date) {
		return new Attendance(UUID.randomUUID(), USER_ID, date, NOW);
	}

	private void insertReturnsSavedAttendance(LocalDate date) {
		when(attendanceRepository.insert(any())).thenReturn(saved(date));
	}

	private void claimInsertAssignsId() {
		when(claimRepository.insert(any())).thenAnswer(invocation -> {
			AttendanceRewardClaim claim = invocation.getArgument(0);
			return new AttendanceRewardClaim(UUID.randomUUID(), claim.getUserId(), claim.getAttendanceId(),
					claim.getRewardType(), claim.getRewardPolicyId(), claim.getStreakPolicyId(),
					claim.getRewardDate(), claim.getMilestoneDays(), claim.getSourceKey(), claim.getTicketCount());
		});
	}

	private void grantSucceeds() {
		when(grantService.grant(any())).thenAnswer(invocation -> {
			GrantCommand command = invocation.getArgument(0);
			return new GrantResult(UUID.randomUUID(), UUID.randomUUID(), command.getQuantity(), 1, NOW,
					Instant.parse("2026-09-30T15:00:00Z"), false);
		});
	}

	private void streakSavedAsIs() {
		when(streakRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private static void assertErrorCode(Runnable call, AttendanceErrorCode expected) {
		assertThatThrownBy(call::run)
				.isInstanceOf(AttendanceException.class)
				.extracting(error -> ((AttendanceException) error).getErrorCode())
				.isEqualTo(expected);
	}

	@Test
	@DisplayName("그 달 첫 출석은 연속 1일로 시작하고 일일 보상만 청구·지급한다")
	void firstAttendanceOfMonth() {
		// given
		when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
		insertReturnsSavedAttendance(TODAY);
		when(streakRepository.findForUpdate(USER_ID, SEPTEMBER)).thenReturn(Optional.empty());
		when(policyRepository.findStreakPolicySet(SEPTEMBER)).thenReturn(Optional.of(SET));
		streakSavedAsIs();
		when(policyRepository.findDailyPolicy(any())).thenReturn(Optional.of(DAILY));
		when(policyRepository.findStreakPolicySetById(SET.getId())).thenReturn(Optional.of(SET));
		claimInsertAssignsId();
		grantSucceeds();
		// when
		AttendanceReceipt receipt = recorder.record(USER_ID, NOW);
		// then
		assertThat(receipt.isCreated()).isTrue();
		assertThat(receipt.getAttendanceDate()).isEqualTo(TODAY);
		assertThat(receipt.getConsecutiveDays()).isEqualTo(1);
		assertThat(receipt.getRewards()).singleElement().satisfies(reward -> {
			assertThat(reward.getRewardType()).isEqualTo(AttendanceRewardType.DAILY);
			assertThat(reward.getTicketCount()).isEqualTo(1);
		});
		ArgumentCaptor<AttendanceStreak> streak = ArgumentCaptor.forClass(AttendanceStreak.class);
		verify(streakRepository).save(streak.capture());
		assertThat(streak.getValue().getPolicySetId()).isEqualTo(SET.getId());
		assertThat(streak.getValue().getConsecutiveDays()).isEqualTo(1);
	}

	@Test
	@DisplayName("일일 정책은 넘겨받은 요청 시각으로 조회한다")
	void looksUpDailyPolicyAtRequestedTime() {
		// given
		when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
		insertReturnsSavedAttendance(TODAY);
		when(streakRepository.findForUpdate(USER_ID, SEPTEMBER)).thenReturn(Optional.empty());
		when(policyRepository.findStreakPolicySet(SEPTEMBER)).thenReturn(Optional.of(SET));
		streakSavedAsIs();
		when(policyRepository.findDailyPolicy(any())).thenReturn(Optional.of(DAILY));
		when(policyRepository.findStreakPolicySetById(SET.getId())).thenReturn(Optional.of(SET));
		claimInsertAssignsId();
		grantSucceeds();
		// when
		recorder.record(USER_ID, NOW);
		// then
		verify(policyRepository).findDailyPolicy(NOW);
	}

	@Test
	@DisplayName("연속 7일째 출석이면 일일 보상 다음에 7일 단계 보상을 청구·지급한다")
	void reachesMilestone() {
		// given: 9/1~9/6 연속 출석한 현황
		AttendanceStreak sixDays = new AttendanceStreak(UUID.randomUUID(), USER_ID, SET.getId(), SEPTEMBER, 6,
				TODAY.minusDays(1));
		when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
		insertReturnsSavedAttendance(TODAY);
		when(streakRepository.findForUpdate(USER_ID, SEPTEMBER)).thenReturn(Optional.of(sixDays));
		streakSavedAsIs();
		when(policyRepository.findDailyPolicy(any())).thenReturn(Optional.of(DAILY));
		when(policyRepository.findStreakPolicySetById(SET.getId())).thenReturn(Optional.of(SET));
		when(claimRepository.exists(USER_ID, AttendanceRewardType.STREAK, "2026-09:7")).thenReturn(false);
		claimInsertAssignsId();
		grantSucceeds();
		// when
		AttendanceReceipt receipt = recorder.record(USER_ID, NOW);
		// then
		assertThat(receipt.getConsecutiveDays()).isEqualTo(7);
		assertThat(receipt.getRewards()).extracting(AttendanceRewardReceipt::getRewardType)
				.containsExactly(AttendanceRewardType.DAILY, AttendanceRewardType.STREAK);
		assertThat(receipt.getRewards()).extracting(AttendanceRewardReceipt::getMilestoneDays)
				.containsExactly(null, 7);
		ArgumentCaptor<GrantCommand> commands = ArgumentCaptor.forClass(GrantCommand.class);
		verify(grantService, times(2)).grant(commands.capture());
		assertThat(commands.getAllValues()).extracting(command -> command.getSource().getType())
				.containsOnly(GrantSourceType.ATTENDANCE);
		assertThat(commands.getAllValues()).extracting(GrantCommand::getQuantity).containsExactly(1L, 1L);
		assertThat(commands.getAllValues()).extracting(GrantCommand::getReason)
				.containsExactly("출석 보상", "연속 출석 7일 보상");
		verify(policyRepository, never()).findStreakPolicySet(any());
	}

	@Test
	@DisplayName("이번 달 같은 단계 보상을 이미 받았으면 다시 도달해도 단계 보상을 청구하지 않는다")
	void doesNotRepeatMilestoneInSameMonth() {
		// given
		AttendanceStreak sixDays = new AttendanceStreak(UUID.randomUUID(), USER_ID, SET.getId(), SEPTEMBER, 6,
				TODAY.minusDays(1));
		when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
		insertReturnsSavedAttendance(TODAY);
		when(streakRepository.findForUpdate(USER_ID, SEPTEMBER)).thenReturn(Optional.of(sixDays));
		streakSavedAsIs();
		when(policyRepository.findDailyPolicy(any())).thenReturn(Optional.of(DAILY));
		when(policyRepository.findStreakPolicySetById(SET.getId())).thenReturn(Optional.of(SET));
		when(claimRepository.exists(USER_ID, AttendanceRewardType.STREAK, "2026-09:7")).thenReturn(true);
		claimInsertAssignsId();
		grantSucceeds();
		// when
		AttendanceReceipt receipt = recorder.record(USER_ID, NOW);
		// then
		assertThat(receipt.getRewards()).extracting(AttendanceRewardReceipt::getRewardType)
				.containsExactly(AttendanceRewardType.DAILY);
		verify(grantService, times(1)).grant(any());
	}

	@Test
	@DisplayName("오늘 이미 출석했으면 새로 기록·지급하지 않고 저장된 청구의 지급 결과로 영수증을 돌려준다")
	void returnsExistingReceipt() {
		// given
		Attendance existing = saved(TODAY);
		AttendanceRewardClaim dailyClaim = new AttendanceRewardClaim(UUID.randomUUID(), USER_ID, existing.getId(),
				AttendanceRewardType.DAILY, DAILY.getId(), null, TODAY, null, "2026-09-07", 1);
		GrantResult granted = new GrantResult(UUID.randomUUID(), UUID.randomUUID(), 1, 3, NOW,
				Instant.parse("2026-09-30T15:00:00Z"), true);
		when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.of(existing));
		when(claimRepository.findByAttendanceId(existing.getId())).thenReturn(List.of(dailyClaim));
		when(grantService.findGrant(refEq(new GrantSource(GrantSourceType.ATTENDANCE, dailyClaim.getId()))))
				.thenReturn(Optional.of(granted));
		when(streakRepository.find(USER_ID, SEPTEMBER)).thenReturn(Optional.of(
				new AttendanceStreak(UUID.randomUUID(), USER_ID, SET.getId(), SEPTEMBER, 3, TODAY)));
		// when
		AttendanceReceipt receipt = recorder.record(USER_ID, NOW);
		// then
		assertThat(receipt.isCreated()).isFalse();
		assertThat(receipt.getAttendanceId()).isEqualTo(existing.getId());
		assertThat(receipt.getConsecutiveDays()).isEqualTo(3);
		assertThat(receipt.getRewards()).usingRecursiveFieldByFieldElementComparator()
				.containsExactly(new AttendanceRewardReceipt(dailyClaim.getId(),
				AttendanceRewardType.DAILY, null, 1, granted.getGrantedAt(), granted.getExpiresAt()));
		verify(attendanceRepository, never()).insert(any());
		verify(grantService, never()).grant(any());
	}

	@Test
	@DisplayName("업무일은 KST 기준이다. UTC로 9/30 15:30은 10/1 출석이며 10월 현황으로 새로 시작한다")
	void usesKstBusinessDate() {
		// given
		Instant utcSeptemberKstOctober = Instant.parse("2026-09-30T15:30:00Z");
		LocalDate october = LocalDate.parse("2026-10-01");
		when(attendanceRepository.findByUserIdAndDate(USER_ID, october)).thenReturn(Optional.empty());
		insertReturnsSavedAttendance(october);
		when(streakRepository.findForUpdate(USER_ID, october)).thenReturn(Optional.empty());
		when(policyRepository.findStreakPolicySet(october)).thenReturn(Optional.of(SET));
		streakSavedAsIs();
		when(policyRepository.findDailyPolicy(any())).thenReturn(Optional.of(DAILY));
		when(policyRepository.findStreakPolicySetById(SET.getId())).thenReturn(Optional.of(SET));
		claimInsertAssignsId();
		grantSucceeds();
		// when
		AttendanceReceipt receipt = recorder.record(USER_ID, utcSeptemberKstOctober);
		// then
		assertThat(receipt.getAttendanceDate()).isEqualTo(october);
		assertThat(receipt.getConsecutiveDays()).isEqualTo(1);
	}

	@Test
	@DisplayName("출석 시각에 적용할 일일 정책이 없으면 ATTENDANCE-001로 실패하고 지급하지 않는다")
	void failsWithoutDailyPolicy() {
		// given
		when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
		when(policyRepository.findDailyPolicy(any())).thenReturn(Optional.empty());
		// when
		// then
		assertErrorCode(() -> recorder.record(USER_ID, NOW), AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND);
		verify(attendanceRepository, never()).insert(any());
		verify(grantService, never()).grant(any());
	}

	@Test
	@DisplayName("그 달에 적용할 연속 출석 정책 묶음이 없으면 ATTENDANCE-001로 실패한다")
	void failsWithoutStreakPolicy() {
		// given
		when(attendanceRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(Optional.empty());
		when(policyRepository.findDailyPolicy(any())).thenReturn(Optional.of(DAILY));
		insertReturnsSavedAttendance(TODAY);
		when(streakRepository.findForUpdate(USER_ID, SEPTEMBER)).thenReturn(Optional.empty());
		when(policyRepository.findStreakPolicySet(SEPTEMBER)).thenReturn(Optional.empty());
		// when
		// then
		assertErrorCode(() -> recorder.record(USER_ID, NOW), AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND);
		verify(grantService, never()).grant(any());
	}
}
