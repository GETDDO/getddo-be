package com.getddo.db.event.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.db.event.entity.EventEntity;

public interface EventJpaRepository extends JpaRepository<EventEntity, UUID> {
	/** 경품을 읽기 전에 이벤트 행부터 잠가 동일 이벤트의 수정·예약 생산자를 직렬화한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select e from EventEntity e where e.id = :id and e.deletedAt is null")
	Optional<EventEntity> findByIdForUpdate(@Param("id") UUID eventId);
}
