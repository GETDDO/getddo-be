package com.getddo.db.attendance.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import com.getddo.core.attendance.domain.AttendanceStreak;
import com.getddo.core.attendance.repository.AttendanceStreakRepository;
import com.getddo.db.attendance.entity.AttendanceStreakEntity;
import com.getddo.db.attendance.mapper.AttendanceMapper;

/** 월별 연속 출석 현황 저장소 구현. */
@Repository
public class AttendanceStreakRepositoryImpl implements AttendanceStreakRepository {

	private final EntityManager entityManager;
	private final AttendanceStreakJpaRepository streakJpaRepository;

	public AttendanceStreakRepositoryImpl(EntityManager entityManager,
			AttendanceStreakJpaRepository streakJpaRepository) {
		this.entityManager = entityManager;
		this.streakJpaRepository = streakJpaRepository;
	}

	@Override
	public Optional<AttendanceStreak> findForUpdate(UUID userId, LocalDate streakMonth) {
		return streakJpaRepository.findByUserIdAndStreakMonth(userId, streakMonth).map(AttendanceMapper::toDomain);
	}

	@Override
	public Optional<AttendanceStreak> find(UUID userId, LocalDate streakMonth) {
		return streakJpaRepository.readByUserIdAndStreakMonth(userId, streakMonth).map(AttendanceMapper::toDomain);
	}

	@Override
	public AttendanceStreak save(AttendanceStreak streak) {
		if (streak.getId() == null) {
			return AttendanceMapper.toDomain(streakJpaRepository.save(AttendanceMapper.toEntity(streak)));
		}
		// findForUpdate로 잠근 Entity가 영속성 컨텍스트에 있으므로 추가 조회 없이 반환된다.
		AttendanceStreakEntity entity = entityManager.find(AttendanceStreakEntity.class, streak.getId());
		entity.applyAttendance(streak.getConsecutiveDays(), streak.getLastAttendanceDate());
		return AttendanceMapper.toDomain(entity);
	}
}
