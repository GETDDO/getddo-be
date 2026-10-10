package com.getddo.core.attendance.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.attendance.domain.AttendanceMilestoneStatus;
import com.getddo.core.attendance.domain.AttendanceMonth;
import com.getddo.core.attendance.domain.AttendanceRewardClaim;
import com.getddo.core.attendance.domain.AttendanceStreak;
import com.getddo.core.attendance.domain.AttendanceToday;
import com.getddo.core.attendance.domain.DailyRewardPolicy;
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
import com.getddo.core.ticket.service.TicketGrantService;

/**
 * 출석 현황(오늘)과 월별 출석을 조회한다.
 *
 * <p>조회만 하므로 읽기 전용 트랜잭션 하나로 묶어, 출석·연속 현황·청구를 같은 시점의 값으로 읽는다.
 * 출석 기록과 보상은 바꾸지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class AttendanceQueryService {

	private final AttendanceRepository attendanceRepository;
	private final AttendanceStreakRepository streakRepository;
	private final AttendanceRewardClaimRepository claimRepository;
	private final AttendancePolicyRepository policyRepository;
	private final TicketGrantService grantService;
	private final TimeProvider timeProvider;

	/**
	 * 오늘(KST 업무일) 출석 현황을 조회한다.
	 *
	 * <p>연속 일수는 오늘 기준으로 이어지고 있을 때만 저장값을 쓴다. 오늘 출석했거나 어제 출석했으면 저장된 값이고,
	 * 어제도 출석하지 않았거나 이번 달 기록이 없으면 0이다. 단계 목록은 이번 달 출석 기록이 있으면 그 달에 고정된
	 * 정책 묶음을, 없으면 이번 달에 적용될 묶음을 쓴다.</p>
	 *
	 * @param userId 사용자 ID
	 * @return 오늘 출석 현황
	 * @throws AttendanceException 적용할 일일 정책이나 이번 달 단계 묶음이 없는 경우({@code ATTENDANCE-001})
	 */
	@Transactional(readOnly = true)
	public AttendanceToday getToday(UUID userId) {
		Instant now = timeProvider.now();
		LocalDate today = timeProvider.businessDate(now);
		LocalDate month = today.withDayOfMonth(1);

		DailyRewardPolicy dailyPolicy = policyRepository.findDailyPolicy(now)
				.orElseThrow(AttendanceQueryService::policyNotFound);
		Optional<AttendanceStreak> streak = streakRepository.find(userId, month);
		StreakPolicySet policySet = streakPolicySet(streak, month).orElseThrow(AttendanceQueryService::policyNotFound);

		boolean attended = attendanceRepository.findByUserIdAndDate(userId, today).isPresent();
		int consecutiveDays = streak.filter(current -> continuesAt(current, today))
				.map(AttendanceStreak::getConsecutiveDays)
				.orElse(0);
		return new AttendanceToday(today, attended, consecutiveDays, dailyPolicy.getRewardTicketCount(),
				milestoneStatuses(userId, YearMonth.from(today), policySet),
				timeProvider.toUtc(today.plusDays(1).atStartOfDay()), now);
	}

	/**
	 * 한 달의 출석 날짜와 그 달에 적용된 단계 현황을 조회한다.
	 *
	 * <p>기록이 없는 달이나 미래의 달도 오류가 아니며 날짜는 빈 목록이다. 그 달의 출석 기록이 있으면 기록에 고정된
	 * 정책 묶음을, 없으면 그 달에 적용될 묶음을 쓰고 적용할 묶음이 없으면 단계 목록도 빈 목록이다.</p>
	 *
	 * @param userId 사용자 ID
	 * @param month  조회할 KST 월
	 * @return 월별 출석 현황
	 */
	@Transactional(readOnly = true)
	public AttendanceMonth getMonth(UUID userId, YearMonth month) {
		Instant now = timeProvider.now();
		LocalDate firstDay = month.atDay(1);

		List<LocalDate> dates = attendanceRepository.findAttendanceDates(userId, month);
		Optional<AttendanceStreak> streak = streakRepository.find(userId, firstDay);
		List<AttendanceMilestoneStatus> milestones = streakPolicySet(streak, firstDay)
				.map(policySet -> milestoneStatuses(userId, month, policySet))
				.orElse(List.of());
		return new AttendanceMonth(month, dates, milestones, now);
	}

	/** 출석 기록이 있으면 그 달에 고정된 묶음을, 없으면 그 달에 적용될 묶음을 찾는다. */
	private Optional<StreakPolicySet> streakPolicySet(Optional<AttendanceStreak> streak, LocalDate month) {
		return streak.isPresent()
				? policyRepository.findStreakPolicySetById(streak.get().getPolicySetId())
				: policyRepository.findStreakPolicySet(month);
	}

	/** 마지막 출석이 오늘이거나 어제여야 연속이 이어지고 있다. */
	private static boolean continuesAt(AttendanceStreak streak, LocalDate today) {
		LocalDate last = streak.getLastAttendanceDate();
		return last.equals(today) || last.equals(today.minusDays(1));
	}

	private List<AttendanceMilestoneStatus> milestoneStatuses(UUID userId, YearMonth month,
			StreakPolicySet policySet) {
		Map<Integer, AttendanceRewardClaim> claimsByDays = claimRepository.findStreakClaims(userId, month).stream()
				.collect(Collectors.toMap(AttendanceRewardClaim::getMilestoneDays, Function.identity()));
		return policySet.getMilestones().stream()
				.map(milestone -> {
					AttendanceRewardClaim claim = claimsByDays.get(milestone.getMilestoneDays());
					return new AttendanceMilestoneStatus(milestone.getMilestoneDays(),
							milestone.getRewardTicketCount(), claim != null, claim == null ? null : grantedAt(claim));
				})
				.toList();
	}

	/** 청구로 지급된 응모권의 지급 시각. 지급 기록이 없으면 null이다. */
	private Instant grantedAt(AttendanceRewardClaim claim) {
		return grantService.findGrant(new GrantSource(GrantSourceType.ATTENDANCE, claim.getId()))
				.map(GrantResult::getGrantedAt)
				.orElse(null);
	}

	private static AttendanceException policyNotFound() {
		return new AttendanceException(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND);
	}
}
