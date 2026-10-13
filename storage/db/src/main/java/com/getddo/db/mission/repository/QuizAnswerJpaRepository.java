package com.getddo.db.mission.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.mission.entity.QuizAnswerEntity;

public interface QuizAnswerJpaRepository extends JpaRepository<QuizAnswerEntity, UUID> {

	List<QuizAnswerEntity> findBySubmissionIdInOrderByIdAsc(Collection<UUID> submissionIds);
}
