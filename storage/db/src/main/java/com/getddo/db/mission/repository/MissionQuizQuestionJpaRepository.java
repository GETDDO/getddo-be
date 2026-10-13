package com.getddo.db.mission.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.mission.entity.MissionQuizQuestionEntity;

public interface MissionQuizQuestionJpaRepository extends JpaRepository<MissionQuizQuestionEntity, UUID> {

	List<MissionQuizQuestionEntity> findByMissionIdOrderByDisplayOrderAsc(UUID missionId);
}
