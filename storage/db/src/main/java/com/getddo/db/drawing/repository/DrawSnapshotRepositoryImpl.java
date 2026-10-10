package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.core.drawing.domain.DrawType;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;
import com.getddo.db.drawing.entity.DrawCandidateEntity;
import com.getddo.db.drawing.entity.DrawRunCandidateEntity;
import com.getddo.db.drawing.entity.DrawRunEntity;
import com.getddo.db.drawing.mapper.DrawSnapshotMapper;

/** 원본 잠금 이후 같은 서비스 트랜잭션에서 스냅샷을 JPA로 저장·조회한다. */
@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class DrawSnapshotRepositoryImpl implements DrawSnapshotRepository {
	private final DrawSnapshotSourceReader source;
	private final DrawRunJpaRepository runs;
	private final DrawCandidateJpaRepository candidates;
	private final DrawRunCandidateJpaRepository links;
	private final DrawSnapshotMapper mapper;
	private final EntityManager entityManager;

	@Override
	public Optional<DrawSnapshotSource.Event> lockEvent(UUID eventId) {
		return source.lockEvent(eventId);
	}

	@Override
	public Optional<DrawSnapshot> findInitial(UUID eventId) {
		return runs.findByEventIdAndDrawTypeAndRunNumber(eventId, DrawType.INITIAL, 0)
				.map(run -> mapper.toDomain(run, links.findCandidates(run.getId())));
	}

	@Override
	public List<DrawSnapshotSource.Participant> findParticipants(UUID eventId) {
		return source.findParticipants(eventId);
	}

	@Override
	public DrawSnapshot saveInitial(DrawSnapshot snapshot) {
		DrawRunEntity run = runs.saveAndFlush(new DrawRunEntity(snapshot.eventId()));
		List<DrawCandidateEntity> savedCandidates = candidates.saveAllAndFlush(snapshot.candidates().stream()
				.map(candidate -> mapper.toEntity(run, candidate)).toList());
		links.saveAllAndFlush(savedCandidates.stream()
				.map(candidate -> new DrawRunCandidateEntity(run, candidate)).toList());
		mapper.fixInput(run, snapshot);
		runs.flush();
		// DATETIME(6)로 저장된 실제 정밀도를 첫 응답에도 사용해 재요청 결과와 일치시킨다.
		entityManager.refresh(run);
		return findInitial(snapshot.eventId()).orElseThrow();
	}
}
