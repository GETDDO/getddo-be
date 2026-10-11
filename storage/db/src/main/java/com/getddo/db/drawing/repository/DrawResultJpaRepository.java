package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.drawing.entity.DrawResultEntity;

public interface DrawResultJpaRepository extends JpaRepository<DrawResultEntity, UUID> {
	/** 실행에 저장된 모든 선정·미충원 결과를 조회한다. 반환 순서는 확정 저장소에서 정렬한다. */
	List<DrawResultEntity> findByDrawRunId(UUID runId);
	/** 실행에 결과 행이 하나라도 있는지 확인하여 부분 결과의 중복 저장을 차단한다. */
	boolean existsByDrawRunId(UUID runId);
}
