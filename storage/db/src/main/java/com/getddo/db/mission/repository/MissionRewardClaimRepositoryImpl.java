package com.getddo.db.mission.repository;

import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.mission.domain.MissionRewardClaim;
import com.getddo.core.mission.repository.MissionRewardClaimRepository;
import com.getddo.db.mission.mapper.MissionMapper;

@Repository
@RequiredArgsConstructor
public class MissionRewardClaimRepositoryImpl implements MissionRewardClaimRepository {

	private final MissionRewardClaimJpaRepository claimJpaRepository;
	private final MissionMapper mapper;

	@Override
	public Optional<MissionRewardClaim> findByUserAndMission(UUID userId, UUID missionId) {
		return claimJpaRepository.findByUserIdAndMissionId(userId, missionId).map(mapper::toDomain);
	}

	@Override
	public MissionRewardClaim insert(MissionRewardClaim claim) {
		return mapper.toDomain(claimJpaRepository.saveAndFlush(mapper.toEntity(claim)));
	}
}
