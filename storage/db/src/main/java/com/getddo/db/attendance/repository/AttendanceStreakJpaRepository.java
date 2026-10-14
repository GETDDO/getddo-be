package com.getddo.db.attendance.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.getddo.db.attendance.entity.AttendanceStreakEntity;

public interface AttendanceStreakJpaRepository extends JpaRepository<AttendanceStreakEntity, UUID> {

	/** 사용자의 해당 월 현황을 {@code SELECT ... FOR UPDATE}로 잠가 조회한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AttendanceStreakEntity> findByUserIdAndStreakMonth(UUID userId, LocalDate streakMonth);

	/** 사용자의 해당 월 현황을 잠그지 않고 조회한다. */
	Optional<AttendanceStreakEntity> readByUserIdAndStreakMonth(UUID userId, LocalDate streakMonth);
}
