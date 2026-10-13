package com.getddo.db.mission.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.mission.entity.QuizQuestionOptionEntity;

public interface QuizQuestionOptionJpaRepository extends JpaRepository<QuizQuestionOptionEntity, UUID> {

	List<QuizQuestionOptionEntity> findByQuizQuestionIdInOrderByDisplayOrderAsc(Collection<UUID> quizQuestionIds);
}
