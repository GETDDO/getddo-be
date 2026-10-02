package com.getddo.db.attendance.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.core.attendance.domain.AttendanceRewardType;
import com.getddo.db.attendance.entity.AttendanceRewardClaimEntity;

public interface AttendanceRewardClaimJpaRepository extends JpaRepository<AttendanceRewardClaimEntity, UUID> {

	List<AttendanceRewardClaimEntity> findByAttendanceId(UUID attendanceId);

	boolean existsByUserIdAndRewardTypeAndSourceKey(UUID userId, AttendanceRewardType rewardType, String sourceKey);
}
