package com.getddo.db.mission.repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.mission.domain.QuizQuestionProgress;
import com.getddo.core.mission.repository.QuizQuestionProgressRepository;
import com.getddo.db.mission.mapper.MissionMapper;

@Repository
@RequiredArgsConstructor
public class QuizQuestionProgressRepositoryImpl implements QuizQuestionProgressRepository {

	private final QuizQuestionProgressJpaRepository progressJpaRepository;
	private final MissionMapper mapper;

	@Override
	public QuizQuestionProgress insert(QuizQuestionProgress progress) {
		return mapper.toDomain(progressJpaRepository.saveAndFlush(mapper.toEntity(progress)));
	}

	@Override
	public Set<UUID> findCorrectQuestionIds(UUID userId, Collection<UUID> questionIds) {
		if (questionIds.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(progressJpaRepository.findQuestionIds(userId, questionIds));
	}
}
