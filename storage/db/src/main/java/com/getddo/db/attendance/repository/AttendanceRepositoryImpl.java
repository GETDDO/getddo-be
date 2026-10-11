package com.getddo.db.attendance.repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.attendance.domain.Attendance;
import com.getddo.core.attendance.repository.AttendanceRepository;
import com.getddo.db.attendance.entity.AttendanceEntity;
import com.getddo.db.attendance.mapper.AttendanceMapper;

@Repository
@RequiredArgsConstructor
public class AttendanceRepositoryImpl implements AttendanceRepository {

	private final AttendanceJpaRepository attendanceJpaRepository;
	private final AttendanceMapper mapper;

	@Override
	public Optional<Attendance> findByUserIdAndDate(UUID userId, LocalDate attendanceDate) {
		return attendanceJpaRepository.findByUserIdAndAttendanceDate(userId, attendanceDate).map(mapper::toDomain);
	}

	@Override
	public List<LocalDate> findAttendanceDates(UUID userId, YearMonth month) {
		return attendanceJpaRepository.findAttendanceDates(userId, month.atDay(1), month.atEndOfMonth());
	}

	@Override
	public Attendance insert(Attendance attendance) {
		AttendanceEntity saved = attendanceJpaRepository.saveAndFlush(mapper.toEntity(attendance));
		return mapper.toDomain(saved);
	}
}
