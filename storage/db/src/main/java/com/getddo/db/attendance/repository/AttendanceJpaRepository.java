package com.getddo.db.attendance.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.attendance.entity.AttendanceEntity;

public interface AttendanceJpaRepository extends JpaRepository<AttendanceEntity, UUID> {

	Optional<AttendanceEntity> findByUserIdAndAttendanceDate(UUID userId, LocalDate attendanceDate);
}
