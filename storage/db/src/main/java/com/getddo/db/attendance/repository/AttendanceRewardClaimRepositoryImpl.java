package com.getddo.db.attendance.repository;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.attendance.domain.AttendanceRewardClaim;
import com.getddo.core.attendance.domain.AttendanceRewardType;
import com.getddo.core.attendance.repository.AttendanceRewardClaimRepository;
import com.getddo.db.attendance.mapper.AttendanceMapper;

@Repository
@RequiredArgsConstructor
public class AttendanceRewardClaimRepositoryImpl implements AttendanceRewardClaimRepository {

	/** 일일 보상이 먼저, 단계 보상은 단계 일수 오름차순. */
	private static final Comparator<AttendanceRewardClaim> RECEIPT_ORDER = Comparator
			.comparing((AttendanceRewardClaim claim) -> claim.getRewardType() != AttendanceRewardType.DAILY)
			.thenComparing(claim -> claim.getMilestoneDays() == null ? 0 : claim.getMilestoneDays());

	private final AttendanceRewardClaimJpaRepository claimJpaRepository;
	private final AttendanceMapper mapper;

	@Override
	public List<AttendanceRewardClaim> findByAttendanceId(UUID attendanceId) {
		return claimJpaRepository.findByAttendanceId(attendanceId).stream()
				.map(mapper::toDomain)
				.sorted(RECEIPT_ORDER)
				.toList();
	}

	@Override
	public List<AttendanceRewardClaim> findStreakClaims(UUID userId, YearMonth month) {
		return claimJpaRepository
				.findByUserIdAndRewardTypeAndRewardDateBetweenOrderByMilestoneDaysAsc(userId,
						AttendanceRewardType.STREAK, month.atDay(1), month.atEndOfMonth())
				.stream()
				.map(mapper::toDomain)
				.toList();
	}

	@Override
	public boolean exists(UUID userId, AttendanceRewardType rewardType, String sourceKey) {
		return claimJpaRepository.existsByUserIdAndRewardTypeAndSourceKey(userId, rewardType, sourceKey);
	}

	@Override
	public AttendanceRewardClaim insert(AttendanceRewardClaim claim) {
		return mapper.toDomain(claimJpaRepository.save(mapper.toEntity(claim)));
	}
}
