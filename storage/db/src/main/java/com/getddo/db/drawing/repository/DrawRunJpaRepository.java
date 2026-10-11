package com.getddo.db.drawing.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.core.drawing.domain.DrawType;
import com.getddo.db.drawing.entity.DrawRunEntity;

public interface DrawRunJpaRepository extends JpaRepository<DrawRunEntity, UUID> {
	/** 이벤트·실행 종류·차수로 같은 실행을 찾는다. 최초 실행은 INITIAL과 차수 0으로 식별한다. */
	Optional<DrawRunEntity> findByEventIdAndDrawTypeAndRunNumber(UUID eventId, DrawType drawType, int runNumber);
	/** 서비스 트랜잭션에서 실행을 쓰기 잠금으로 조회한다. 호출자는 이벤트 잠금을 먼저 확보해야 한다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select run from DrawRunEntity run where run.id = :runId")
	Optional<DrawRunEntity> findForUpdate(@Param("runId") UUID runId);
}
