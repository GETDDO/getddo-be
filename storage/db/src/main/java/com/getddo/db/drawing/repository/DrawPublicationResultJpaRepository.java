package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.UUID;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import com.getddo.db.drawing.entity.DrawPublicationResultEntity;
import com.getddo.db.drawing.entity.DrawPublicationResultId;

public interface DrawPublicationResultJpaRepository
		extends JpaRepository<DrawPublicationResultEntity, DrawPublicationResultId> {
	List<DrawPublicationResultEntity> findByIdPublicationIdOrderByIdResultId(UUID publicationId);
	boolean existsByIdResultId(UUID resultId);
	List<DrawPublicationResultEntity> findByIdPublicationIdIn(Set<UUID> publicationIds);
}
