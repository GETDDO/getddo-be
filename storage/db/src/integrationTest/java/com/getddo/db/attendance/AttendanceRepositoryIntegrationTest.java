package com.getddo.db.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.getddo.core.attendance.domain.Attendance;
import com.getddo.core.attendance.domain.AttendanceRewardClaim;
import com.getddo.core.attendance.domain.AttendanceRewardType;
import com.getddo.core.attendance.domain.AttendanceStreak;
import com.getddo.core.attendance.domain.DailyRewardPolicy;
import com.getddo.core.attendance.domain.StreakMilestone;
import com.getddo.core.attendance.domain.StreakPolicySet;
import com.getddo.core.attendance.repository.AttendancePolicyRepository;
import com.getddo.core.attendance.repository.AttendanceRepository;
import com.getddo.core.attendance.repository.AttendanceRewardClaimRepository;
import com.getddo.core.attendance.repository.AttendanceStreakRepository;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 출석 Entity 매핑과 저장소 구현을 Flyway로 만든 실제 MySQL 스키마(V004)에서 검증한다. */
class AttendanceRepositoryIntegrationTest extends AttendanceIntegrationTestSupport {

	private static final LocalDate SEPT_15 = LocalDate.parse("2026-09-15");
	private static final LocalDate SEPTEMBER = LocalDate.parse("2026-09-01");

	@Autowired
	private AttendanceRepository attendanceRepository;
	@Autowired
	private AttendanceStreakRepository streakRepository;
	@Autowired
	private AttendanceRewardClaimRepository claimRepository;
	@Autowired
	private AttendancePolicyRepository policyRepository;

	@Test
	@DisplayName("출석을 저장하면 ID와 기록 시각이 채워지고 사용자·날짜로 다시 찾는다")
	void insertsAndFindsAttendance() {
		// given
		// when
		Attendance saved = transaction.execute(status ->
				attendanceRepository.insert(Attendance.create(userId, SEPT_15)));
		// then
		Optional<Attendance> sameDay = transaction.execute(status ->
				attendanceRepository.findByUserIdAndDate(userId, SEPT_15));
		Optional<Attendance> nextDay = transaction.execute(status ->
				attendanceRepository.findByUserIdAndDate(userId, SEPT_15.plusDays(1)));
		assertThat(saved.getId().version()).isEqualTo(7);
		assertThat(saved.getCreatedAt()).isEqualTo(clock.instant());
		assertThat(sameDay).contains(saved);
		assertThat(nextDay).isEmpty();
	}

	@Test
	@DisplayName("같은 사용자·같은 날짜의 두 번째 출석은 저장 시점에 바로 UNIQUE 위반으로 실패한다")
	void duplicateAttendanceFailsOnInsert() {
		// given
		transaction.executeWithoutResult(status -> attendanceRepository.insert(Attendance.create(userId, SEPT_15)));
		// when
		// then
		assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
				attendanceRepository.insert(Attendance.create(userId, SEPT_15))))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(count("select count(*) from attendances where user_id = ?", bytes(userId))).isEqualTo(1);
	}

	@Test
	@DisplayName("연속 출석 현황을 새로 만들고, 잠가 읽은 뒤 갱신한다")
	void savesAndUpdatesStreak() {
		// given
		UUID setId = policies.streakPolicySet(adminId, SEPTEMBER, AttendanceSeeds.DEFAULT_MILESTONES);
		transaction.executeWithoutResult(status ->
				streakRepository.save(AttendanceStreak.start(userId, setId, SEPT_15)));
		// when
		AttendanceStreak updated = transaction.execute(status -> {
			AttendanceStreak locked = streakRepository.findForUpdate(userId, SEPTEMBER).orElseThrow();
			return streakRepository.save(locked.attend(SEPT_15.plusDays(1)));
		});
		// then
		AttendanceStreak reloaded = transaction.execute(status ->
				streakRepository.findForUpdate(userId, SEPTEMBER).orElseThrow());
		assertThat(reloaded).isEqualTo(updated);
		assertThat(reloaded.getConsecutiveDays()).isEqualTo(2);
		assertThat(reloaded.getLastAttendanceDate()).isEqualTo(SEPT_15.plusDays(1));
		assertThat(reloaded.getPolicySetId()).isEqualTo(setId);
		Optional<AttendanceStreak> october = transaction.execute(status ->
				streakRepository.findForUpdate(userId, SEPTEMBER.plusMonths(1)));
		assertThat(october).isEmpty();
	}

	@Test
	@DisplayName("일일·단계 청구를 저장하고 출석별로 일일 보상 먼저, 단계 일수 순으로 읽으며 중복 키를 확인한다")
	void savesClaimsInReceiptOrder() {
		// given
		UUID dailyPolicyId = policies.dailyPolicy(adminId, 1, Instant.parse("2026-09-01T00:00:00Z"), null);
		UUID setId = policies.streakPolicySet(adminId, SEPTEMBER, AttendanceSeeds.DEFAULT_MILESTONES);
		DailyRewardPolicy daily = new DailyRewardPolicy(dailyPolicyId, 1);
		StreakMilestone seven = new StreakMilestone(policies.milestoneId(setId, 7), 7, 1);
		StreakMilestone fourteen = new StreakMilestone(policies.milestoneId(setId, 14), 14, 3);
		// when
		List<AttendanceRewardClaim> saved = transaction.execute(status -> {
			Attendance attendance = attendanceRepository.insert(Attendance.create(userId, SEPT_15));
			// 저장 순서와 관계없이 일일 → 단계 일수 순으로 읽히는지 보려고 거꾸로 넣는다
			claimRepository.insert(AttendanceRewardClaim.streak(attendance, fourteen));
			claimRepository.insert(AttendanceRewardClaim.streak(attendance, seven));
			claimRepository.insert(AttendanceRewardClaim.daily(attendance, daily));
			return claimRepository.findByAttendanceId(attendance.getId());
		});
		// then
		assertThat(saved).extracting(AttendanceRewardClaim::getRewardType).containsExactly(
				AttendanceRewardType.DAILY, AttendanceRewardType.STREAK, AttendanceRewardType.STREAK);
		assertThat(saved).extracting(AttendanceRewardClaim::getSourceKey)
				.containsExactly("2026-09-15", "2026-09:7", "2026-09:14");
		assertThat(saved).extracting(AttendanceRewardClaim::getTicketCount).containsExactly(1, 1, 3);
		assertThat(saved.get(0).getRewardPolicyId()).isEqualTo(dailyPolicyId);
		assertThat(saved.get(1).getStreakPolicyId()).isEqualTo(seven.getId());
		boolean septemberSeven = transaction.execute(status ->
				claimRepository.exists(userId, AttendanceRewardType.STREAK, "2026-09:7"));
		boolean octoberSeven = transaction.execute(status ->
				claimRepository.exists(userId, AttendanceRewardType.STREAK, "2026-10:7"));
		assertThat(septemberSeven).isTrue();
		assertThat(octoberSeven).isFalse();
	}

	@Test
	@DisplayName("출석 시각에 유효한 일일 정책을 고르고, 기간이 겹치면 가장 늦게 시작한 정책을 쓴다")
	void findsDailyPolicyEffectiveAt() {
		// given
		Instant september = Instant.parse("2026-08-31T15:00:00Z");
		Instant october = Instant.parse("2026-09-30T15:00:00Z");
		UUID old = policies.dailyPolicy(adminId, 1, september, october);
		UUID newer = policies.dailyPolicy(adminId, 2, october, null);
		// when
		Optional<DailyRewardPolicy> beforeSeptember = transaction.execute(status ->
				policyRepository.findDailyPolicy(september.minusNanos(1000)));
		Optional<DailyRewardPolicy> inSeptember = transaction.execute(status ->
				policyRepository.findDailyPolicy(october.minusNanos(1000)));
		Optional<DailyRewardPolicy> atOctober = transaction.execute(status ->
				policyRepository.findDailyPolicy(october));
		// then
		assertThat(beforeSeptember).isEmpty();
		assertThat(inSeptember).contains(new DailyRewardPolicy(old, 1));
		assertThat(atOctober).contains(new DailyRewardPolicy(newer, 2));
	}

	@Test
	@DisplayName("대상 월 이하에서 가장 최근에 시작한 연속 정책 묶음과 그 단계를 단계 일수 순으로 읽는다")
	void findsStreakPolicySetForMonth() {
		// given
		UUID july = policies.streakPolicySet(adminId, LocalDate.parse("2026-07-01"), AttendanceSeeds.DEFAULT_MILESTONES);
		UUID october = policies.streakPolicySet(adminId, LocalDate.parse("2026-10-01"), Map.of(5, 2, 10, 4));
		// when
		Optional<StreakPolicySet> june = transaction.execute(status ->
				policyRepository.findStreakPolicySet(LocalDate.parse("2026-06-01")));
		Optional<StreakPolicySet> september = transaction.execute(status ->
				policyRepository.findStreakPolicySet(SEPTEMBER));
		Optional<StreakPolicySet> november = transaction.execute(status ->
				policyRepository.findStreakPolicySet(LocalDate.parse("2026-11-01")));
		Optional<StreakPolicySet> byId = transaction.execute(status ->
				policyRepository.findStreakPolicySetById(july));
		// then
		assertThat(june).isEmpty();
		assertThat(september).map(StreakPolicySet::getId).contains(july);
		assertThat(september.orElseThrow().getMilestones()).extracting(StreakMilestone::getMilestoneDays)
				.containsExactly(7, 14, 28);
		assertThat(september.orElseThrow().getMilestones()).extracting(StreakMilestone::getRewardTicketCount)
				.containsExactly(1, 3, 7);
		assertThat(november).map(StreakPolicySet::getId).contains(october);
		assertThat(byId).isEqualTo(september);
	}
}
