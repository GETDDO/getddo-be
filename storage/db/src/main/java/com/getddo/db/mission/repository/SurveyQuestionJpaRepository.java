package com.getddo.db.mission.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.mission.entity.SurveyQuestionEntity;

public interface SurveyQuestionJpaRepository extends JpaRepository<SurveyQuestionEntity, UUID> {

	List<SurveyQuestionEntity> findByMissionIdOrderByDisplayOrderAsc(UUID missionId);
}
