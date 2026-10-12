package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import com.getddo.db.drawing.entity.DrawPublicationEntity;

public interface DrawPublicationJpaRepository extends JpaRepository<DrawPublicationEntity, UUID> {
	Optional<DrawPublicationEntity> findFirstByEventIdOrderByRevisionDesc(UUID eventId);
	List<DrawPublicationEntity> findByEventIdOrderByRevisionAsc(UUID eventId);
	long countByEventId(UUID eventId);
	List<DrawPublicationEntity> findByEventIdOrderByRevisionDesc(UUID eventId, Pageable pageable);
	Optional<DrawPublicationEntity> findFirstByEventIdAndRevisionLessThanOrderByRevisionDesc(UUID eventId, int revision);
}
