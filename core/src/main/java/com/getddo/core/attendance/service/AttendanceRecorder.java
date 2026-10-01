package com.getddo.core.attendance.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.getddo.core.attendance.repository.AttendancePolicyRepository;
import com.getddo.core.attendance.repository.AttendanceRepository;
import com.getddo.core.attendance.repository.AttendanceRewardClaimRepository;
import com.getddo.core.attendance.repository.AttendanceStreakRepository;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.GrantCommand;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.service.TicketGrantService;

/**
 * 출석 한 건을 한 트랜잭션에서 처리한다. {@link AttendanceService}만 호출한다.
 *
 * <p>응모권 지급 호출 규약(기존 청구 조회 → 청구 저장 → {@code grant})을 따른다. 오늘 출석이 이미 있으면 새로 기록하지 않고
 * 저장된 청구의 지급 결과를 돌려준다. 출석 기록·연속 현황·보상 청구·응모권 지급은 이 트랜잭션에서 함께 확정되거나 함께
 * 취소된다.</p>
 */
@Service
public class AttendanceRecorder {

	private static final String DAILY_REASON = "출석 보상";

	private final AttendanceRepository attendanceRepository;
	private final AttendanceStreakRepository streakRepository;
	private final AttendanceRewardClaimRepository claimRepository;
	private final AttendancePolicyRepository policyRepository;
	private final TicketGrantService grantService;
	private final TimeProvider timeProvider;

	public AttendanceRecorder(
			AttendanceRepository attendanceRepository,
			AttendanceStreakRepository streakRepository,
			AttendanceRewardClaimRepository claimRepository,
			AttendancePolicyRepository policyRepository,
			TicketGrantService grantService,
			TimeProvider timeProvider) {
		this.attendanceRepository = attendanceRepository;
		this.streakRepository = streakRepository;
		this.claimRepository = claimRepository;
		this.policyRepository = policyRepository;
		this.grantService = grantService;
		this.timeProvider = timeProvider;
	}

	/**
	 * 요청 사용자를 오늘(KST) 출석 처리한다.
	 *
	 * <p>같은 날 동시 요청이 겹치면 늦은 쪽은 출석 저장에서 UNIQUE 위반으로 실패하고 이 트랜잭션 전체가 롤백된다.
	 * 재처리는 호출자({@link AttendanceService})가 새 트랜잭션에서 한다.</p>
	 *
	 * @param userId 요청 사용자 ID
	 * @return 새 출석이면 {@code created=true}, 이미 출석한 날이면 저장된 결과({@code created=false})
	 * @throws BusinessException 적용할 일일 정책이나 그 달의 연속 출석 정책 묶음이 없는 경우
	 */
	@Transactional
	public AttendanceReceipt record(UUID userId) {
		// DATETIME(6)이 저장하는 정밀도로 잘라 정책 적용 시각 판정이 저장 값과 어긋나지 않게 한다.
		Instant now = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
		LocalDate today = timeProvider.businessDate(now);

		Optional<Attendance> existing = attendanceRepository.findByUserIdAndDate(userId, today);
		if (existing.isPresent()) {
			return existingReceipt(existing.get());
		}

		DailyRewardPolicy dailyPolicy = policyRepository.findDailyPolicy(now).orElseThrow(AttendanceRecorder::noPolicy);
		Attendance attendance = attendanceRepository.insert(Attendance.create(userId, today));
		AttendanceStreak streak = streakRepository.save(advanceStreak(userId, today));

		List<AttendanceRewardReceipt> rewards = new ArrayList<>();
		rewards.add(claimAndGrant(AttendanceRewardClaim.daily(attendance, dailyPolicy), DAILY_REASON));
		reachedMilestone(streak)
				.map(milestone -> AttendanceRewardClaim.streak(attendance, milestone))
				.filter(claim -> !claimRepository.exists(userId, AttendanceRewardType.STREAK, claim.getSourceKey()))
				.ifPresent(claim -> rewards.add(claimAndGrant(claim, streakReason(claim.getMilestoneDays()))));

		return new AttendanceReceipt(attendance.getId(), today, streak.getConsecutiveDays(), rewards,
				attendance.getCreatedAt(), true);
	}

	/** 그 달 현황을 잠가 오늘 출석을 반영한다. 그 달 첫 출석이면 그 달에 적용할 정책 묶음으로 새로 시작한다. */
	private AttendanceStreak advanceStreak(UUID userId, LocalDate today) {
		LocalDate month = today.withDayOfMonth(1);
		return streakRepository.findForUpdate(userId, month)
				.map(streak -> streak.attend(today))
				.orElseGet(() -> AttendanceStreak.start(userId, streakPolicySetFor(month).getId(), today));
	}

	private StreakPolicySet streakPolicySetFor(LocalDate month) {
		return policyRepository.findStreakPolicySet(month).orElseThrow(AttendanceRecorder::noPolicy);
	}

	/** 현황에 기록된 이 달의 정책 묶음 기준으로 오늘 연속 일수가 도달한 단계를 찾는다. */
	private Optional<StreakMilestone> reachedMilestone(AttendanceStreak streak) {
		StreakPolicySet policySet = policyRepository.findStreakPolicySetById(streak.getPolicySetId())
				.orElseThrow(AttendanceRecorder::noPolicy);
		return policySet.milestoneReachedAt(streak.getConsecutiveDays());
	}

	private AttendanceRewardReceipt claimAndGrant(AttendanceRewardClaim claim, String reason) {
		AttendanceRewardClaim saved = claimRepository.insert(claim);
		GrantResult granted = grantService.grant(new GrantCommand(saved.getUserId(), grantSource(saved),
				saved.getTicketCount(), reason));
		return toReceipt(saved, granted);
	}

	/** 이미 확정된 출석의 결과. 청구마다 응모권 지급 결과를 다시 읽는다. 새로 지급하지 않는다. */
	private AttendanceReceipt existingReceipt(Attendance attendance) {
		List<AttendanceRewardReceipt> rewards = claimRepository.findByAttendanceId(attendance.getId()).stream()
				.map(claim -> toReceipt(claim, grantService.findGrant(grantSource(claim))
						.orElseThrow(() -> new IllegalStateException("출석 보상 청구에 응모권 지급 결과가 없다."))))
				.toList();
		LocalDate month = attendance.getAttendanceDate().withDayOfMonth(1);
		int consecutiveDays = streakRepository.find(attendance.getUserId(), month)
				.map(AttendanceStreak::getConsecutiveDays)
				.orElseThrow(() -> new IllegalStateException("출석한 달의 연속 출석 현황이 없다."));
		return new AttendanceReceipt(attendance.getId(), attendance.getAttendanceDate(), consecutiveDays, rewards,
				attendance.getCreatedAt(), false);
	}

	private static GrantSource grantSource(AttendanceRewardClaim claim) {
		return new GrantSource(GrantSourceType.ATTENDANCE, claim.getId());
	}

	private static AttendanceRewardReceipt toReceipt(AttendanceRewardClaim claim, GrantResult granted) {
		return new AttendanceRewardReceipt(claim.getId(), claim.getRewardType(), claim.getMilestoneDays(),
				claim.getTicketCount(), granted.getGrantedAt(), granted.getExpiresAt());
	}

	private static String streakReason(int milestoneDays) {
		return "연속 출석 " + milestoneDays + "일 보상";
	}

	private static BusinessException noPolicy() {
		return new BusinessException(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND);
	}
}
