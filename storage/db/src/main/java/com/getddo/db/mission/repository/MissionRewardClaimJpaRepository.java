package com.getddo.db.mission.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.mission.entity.MissionRewardClaimEntity;

public interface MissionRewardClaimJpaRepository extends JpaRepository<MissionRewardClaimEntity, UUID> {

	Optional<MissionRewardClaimEntity> findByUserIdAndMissionId(UUID userId, UUID missionId);
}
