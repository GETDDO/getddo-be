package com.getddo.db.attendance.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.db.attendance.entity.AttendanceEntity;

public interface AttendanceJpaRepository extends JpaRepository<AttendanceEntity, UUID> {

	Optional<AttendanceEntity> findByUserIdAndAttendanceDate(UUID userId, LocalDate attendanceDate);

	@Query("""
			select a.attendanceDate from AttendanceEntity a
			where a.userId = :userId and a.attendanceDate between :from and :to
			order by a.attendanceDate asc
			""")
	List<LocalDate> findAttendanceDates(@Param("userId") UUID userId, @Param("from") LocalDate from,
			@Param("to") LocalDate to);
}
