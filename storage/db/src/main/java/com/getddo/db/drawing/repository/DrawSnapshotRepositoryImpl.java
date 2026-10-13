package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.core.drawing.domain.DrawType;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;
import com.getddo.db.drawing.entity.DrawCandidateEntity;
import com.getddo.db.drawing.entity.DrawRunCandidateEntity;
import com.getddo.db.drawing.entity.DrawRunEntity;
import com.getddo.db.drawing.mapper.DrawSnapshotMapper;

import static com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN;

/** 원본 잠금 이후 같은 서비스 트랜잭션에서 스냅샷을 JPA로 저장·조회한다. */
@Repository
@RequiredArgsConstructor
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

	@Override public DrawSnapshot registerInitial(UUID eventId) {
		Optional<DrawSnapshot> existing = findInitial(eventId);
		if (existing.isPresent()) return existing.get();
		DrawRunEntity run = runs.saveAndFlush(new DrawRunEntity(eventId));
		return mapper.toDomain(run, List.of());
	}
	@Override public Optional<DrawSnapshot> findRegistered(UUID runId) {
		return runs.findById(runId).map(run -> mapper.toDomain(run, links.findCandidates(runId)));
	}
	@Override public DrawSnapshot saveRegisteredInitial(DrawSnapshot snapshot) {
		DrawRunEntity run = runs.findForUpdate(snapshot.runId()).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (run.getDrawType() != DrawType.INITIAL || run.getStatus() != DrawRunStatus.PREPARING
				|| run.getSnapshotFixedAt() != null || !run.getEventId().equals(snapshot.eventId())) {
			throw new BusinessException(INVALID_RUN);
		}
		return saveInput(run, snapshot);
	}

	@Override public DrawSnapshot saveRegisteredReplacement(DrawSnapshot snapshot) {
		DrawRunEntity run = runs.findForUpdate(snapshot.runId()).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (run.getDrawType() != DrawType.REPLACEMENT || run.getStatus() != DrawRunStatus.PREPARING
				|| run.getSnapshotFixedAt() != null || !run.getEventId().equals(snapshot.eventId())
				|| snapshot.drawType() != DrawType.REPLACEMENT || snapshot.status() != DrawRunStatus.READY
				|| snapshot.rules() == null || snapshot.rules().version() != 2 || snapshot.rules().originalRunId() == null) {
			throw new BusinessException(INVALID_RUN);
		}
		List<DrawCandidateEntity> existing = candidates.findAllById(snapshot.candidates().stream().map(DrawSnapshot.Candidate::id).toList());
		if (existing.size() != snapshot.candidates().size()) {
			throw new BusinessException(INVALID_RUN);
		}
		for (DrawCandidateEntity candidate : existing) {
			if (!candidate.getDrawRun().getId().equals(snapshot.rules().originalRunId())) {
				throw new BusinessException(INVALID_RUN);
			}
		}
		links.saveAllAndFlush(existing.stream().map(candidate -> new DrawRunCandidateEntity(run, candidate)).toList());
		mapper.fixInput(run, snapshot);
		runs.flush();
		entityManager.refresh(run);
		return mapper.toDomain(run, existing);
	}

	@Override
	public DrawSnapshot saveInitial(DrawSnapshot snapshot) {
		DrawRunEntity run = runs.saveAndFlush(new DrawRunEntity(snapshot.eventId()));
		return saveInput(run, snapshot);
	}

	private DrawSnapshot saveInput(DrawRunEntity run, DrawSnapshot snapshot) {
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
