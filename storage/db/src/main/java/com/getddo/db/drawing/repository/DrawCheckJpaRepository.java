package com.getddo.db.drawing.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.getddo.db.drawing.entity.DrawCheckEntity;

public interface DrawCheckJpaRepository extends JpaRepository<DrawCheckEntity, UUID> {
	Page<DrawCheckEntity> findByDrawRunId(UUID runId, Pageable pageable);
}
