package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.UUID;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import com.getddo.db.drawing.entity.DrawPublicationResultEntity;
import com.getddo.db.drawing.entity.DrawPublicationResultId;

public interface DrawPublicationResultJpaRepository
		extends JpaRepository<DrawPublicationResultEntity, DrawPublicationResultId> {
	/** 한 공개 버전의 포함 결과를 결과 식별자 순서로 조회한다. */
	List<DrawPublicationResultEntity> findByIdPublicationIdOrderByIdResultId(UUID publicationId);
	/** 결과가 한 번이라도 공개 명단에 포함됐는지 확인한다. */
	boolean existsByIdResultId(UUID resultId);
	/** 여러 공개 버전의 포함 결과를 일괄 조회한다. */
	List<DrawPublicationResultEntity> findByIdPublicationIdIn(Set<UUID> publicationIds);
}
