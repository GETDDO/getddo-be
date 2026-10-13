package com.getddo.db.mission.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.db.mission.entity.QuizQuestionProgressEntity;

public interface QuizQuestionProgressJpaRepository extends JpaRepository<QuizQuestionProgressEntity, UUID> {

	@Query("""
			select p.quizQuestionId from QuizQuestionProgressEntity p
			where p.userId = :userId and p.quizQuestionId in :questionIds
			""")
	List<UUID> findQuestionIds(@Param("userId") UUID userId, @Param("questionIds") Collection<UUID> questionIds);
}
