package com.getddo.db.mission.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.mission.entity.SurveyQuestionOptionEntity;

public interface SurveyQuestionOptionJpaRepository extends JpaRepository<SurveyQuestionOptionEntity, UUID> {

	List<SurveyQuestionOptionEntity> findBySurveyQuestionIdInOrderByDisplayOrderAsc(
			Collection<UUID> surveyQuestionIds);
}
