package com.getddo.db.attendance.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import com.getddo.core.attendance.domain.Attendance;
import com.getddo.core.attendance.repository.AttendanceRepository;
import com.getddo.db.attendance.entity.AttendanceEntity;
import com.getddo.db.attendance.mapper.AttendanceMapper;

/** 출석 기록 저장소 구현. */
@Repository
public class AttendanceRepositoryImpl implements AttendanceRepository {

	private final EntityManager entityManager;
	private final AttendanceJpaRepository attendanceJpaRepository;

	public AttendanceRepositoryImpl(EntityManager entityManager, AttendanceJpaRepository attendanceJpaRepository) {
		this.entityManager = entityManager;
		this.attendanceJpaRepository = attendanceJpaRepository;
	}

	@Override
	public Optional<Attendance> findByUserIdAndDate(UUID userId, LocalDate attendanceDate) {
		return attendanceJpaRepository.findByUserIdAndAttendanceDate(userId, attendanceDate)
				.map(AttendanceMapper::toDomain);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>저장 후 DB 값을 다시 읽는다. Auditing이 채운 생성 시각은 나노초까지 가질 수 있지만 {@code DATETIME(6)}은
	 * 마이크로초로 반올림해 저장하므로, 메모리 값을 그대로 돌려주면 같은 날 재요청에서 읽는 값과 달라진다.</p>
	 */
	@Override
	public Attendance insert(Attendance attendance) {
		AttendanceEntity saved = attendanceJpaRepository.saveAndFlush(AttendanceMapper.toEntity(attendance));
		entityManager.refresh(saved);
		return AttendanceMapper.toDomain(saved);
	}
}
