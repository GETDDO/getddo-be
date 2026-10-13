package com.getddo.db.mission.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.mission.entity.SurveyAnswerEntity;

public interface SurveyAnswerJpaRepository extends JpaRepository<SurveyAnswerEntity, UUID> {

	List<SurveyAnswerEntity> findBySubmissionIdInOrderByIdAsc(Collection<UUID> submissionIds);
}
