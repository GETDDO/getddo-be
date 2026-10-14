package com.getddo.db.attendance.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.attendance.domain.AttendanceStreak;
import com.getddo.core.attendance.repository.AttendanceStreakRepository;
import com.getddo.db.attendance.entity.AttendanceStreakEntity;
import com.getddo.db.attendance.mapper.AttendanceMapper;

@Repository
@RequiredArgsConstructor
public class AttendanceStreakRepositoryImpl implements AttendanceStreakRepository {

	private final EntityManager entityManager;
	private final AttendanceStreakJpaRepository streakJpaRepository;
	private final AttendanceMapper mapper;

	@Override
	public Optional<AttendanceStreak> findForUpdate(UUID userId, LocalDate streakMonth) {
		return streakJpaRepository.findByUserIdAndStreakMonth(userId, streakMonth).map(mapper::toDomain);
	}

	@Override
	public Optional<AttendanceStreak> find(UUID userId, LocalDate streakMonth) {
		return streakJpaRepository.readByUserIdAndStreakMonth(userId, streakMonth).map(mapper::toDomain);
	}

	@Override
	public AttendanceStreak save(AttendanceStreak streak) {
		if (streak.getId() == null) {
			return mapper.toDomain(streakJpaRepository.save(mapper.toEntity(streak)));
		}
		// findForUpdate로 잠근 Entity가 영속성 컨텍스트에 있으므로 추가 조회 없이 반환된다.
		AttendanceStreakEntity entity = entityManager.find(AttendanceStreakEntity.class, streak.getId());
		entity.applyAttendance(streak.getConsecutiveDays(), streak.getLastAttendanceDate());
		return mapper.toDomain(entity);
	}
}
