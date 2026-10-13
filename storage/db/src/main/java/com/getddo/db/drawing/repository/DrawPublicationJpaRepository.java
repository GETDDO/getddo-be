package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import com.getddo.db.drawing.entity.DrawPublicationEntity;

public interface DrawPublicationJpaRepository extends JpaRepository<DrawPublicationEntity, UUID> {
	/** 이벤트의 최신 공개 버전을 조회한다. */
	Optional<DrawPublicationEntity> findFirstByEventIdOrderByRevisionDesc(UUID eventId);
	/** 이벤트의 공개 버전을 오래된 순서로 조회한다. */
	List<DrawPublicationEntity> findByEventIdOrderByRevisionAsc(UUID eventId);
	/** 이벤트에 실제 저장된 공개 버전 수를 반환한다. */
	long countByEventId(UUID eventId);
	/** 이벤트의 공개 버전을 최신순으로 페이지 조회한다. */
	List<DrawPublicationEntity> findByEventIdOrderByRevisionDesc(UUID eventId, Pageable pageable);
	/** 지정 버전 바로 이전의 실제 공개 버전을 조회한다. */
	Optional<DrawPublicationEntity> findFirstByEventIdAndRevisionLessThanOrderByRevisionDesc(UUID eventId, int revision);
}
