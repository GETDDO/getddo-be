package com.getddo.db.mission.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.db.mission.entity.MissionSubmissionEntity;

public interface MissionSubmissionJpaRepository extends JpaRepository<MissionSubmissionEntity, UUID> {

	Optional<MissionSubmissionEntity> findFirstByUserIdAndMissionIdAndCompletedTrueOrderByCreatedAtAscIdAsc(UUID userId,
			UUID missionId);

	Page<MissionSubmissionEntity> findByUserIdAndMissionId(UUID userId, UUID missionId, Pageable pageable);

	@Query("""
			select distinct s.missionId from MissionSubmissionEntity s
			where s.userId = :userId and s.completed = true and s.missionId in :missionIds
			""")
	List<UUID> findCompletedMissionIds(@Param("userId") UUID userId, @Param("missionIds") Collection<UUID> missionIds);
}
