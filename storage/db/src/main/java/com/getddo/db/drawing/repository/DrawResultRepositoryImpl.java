package com.getddo.db.drawing.repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.DrawConfirmation;
import com.getddo.core.drawing.domain.DrawResult;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSelection;
import com.getddo.core.drawing.domain.DrawSelectionResultType;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawType;
import com.getddo.core.drawing.repository.DrawResultRepository;
import com.getddo.db.drawing.entity.DrawResultEntity;
import com.getddo.db.drawing.mapper.DrawResultMapper;
import com.getddo.db.drawing.mapper.DrawSnapshotMapper;

import static com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN;
import static com.getddo.db.common.util.UuidBinary.fromBytes;
import static com.getddo.db.common.util.UuidBinary.toBytes;

/** 결과는 JPA로 저장하며 다른 영역·취소 이력은 읽기 전용 SQL로 검증한다. */
@Repository
@RequiredArgsConstructor
public class DrawResultRepositoryImpl implements DrawResultRepository {
	private final DrawRunJpaRepository runs;
	private final DrawRunCandidateJpaRepository links;
	private final DrawResultJpaRepository results;
	private final DrawSnapshotMapper snapshots;
	private final DrawResultMapper mapper;
	private final JdbcTemplate jdbc;
	private final EntityManager entityManager;

	@Override
	public Optional<UUID> findEventId(UUID runId) {
		return jdbc.query("select event_id from draw_runs where id = ?",
				(row, index) -> fromBytes(row.getBytes("event_id")), toBytes(runId)).stream().findFirst();
	}

	@Override
	public Optional<DrawSnapshot> lockSnapshot(UUID runId) {
		return runs.findForUpdate(runId).map(run -> {
			// 재추첨의 대상 자리·유지 결과 스냅샷 계약은 GD-138에서 연결한다.
			if (run.getDrawType() != DrawType.INITIAL || run.getRunNumber() != 0) invalid();
			return snapshots.toDomain(run, links.findCandidates(runId));
		});
	}

	@Override
	public void validateRelations(DrawSnapshot snapshot) {
		Map<UUID, UUID> owners = new HashMap<>();
		jdbc.query("""
			select c.id, p.user_id, p.event_id as participant_event, origin.event_id as origin_event, origin.id as origin_id
			from draw_run_candidates link join draw_candidates c on c.id = link.candidate_id
			join event_participants p on p.id = c.participant_id
			join draw_runs origin on origin.id = c.draw_run_id
			where link.draw_run_id = ?
			""", (org.springframework.jdbc.core.RowCallbackHandler) row -> {
			if (!snapshot.eventId().equals(fromBytes(row.getBytes("participant_event")))
					|| !snapshot.eventId().equals(fromBytes(row.getBytes("origin_event")))
					|| !snapshot.runId().equals(fromBytes(row.getBytes("origin_id")))) invalid();
			owners.put(fromBytes(row.getBytes("id")), fromBytes(row.getBytes("user_id")));
		}, toBytes(snapshot.runId()));
		if (owners.size() != snapshot.candidates().size() || snapshot.candidates().stream()
				.anyMatch(c -> !c.userId().equals(owners.get(c.id())))) invalid();
		List<UUID> prizeIds = jdbc.query("select id from event_prizes where event_id = ?",
				(row, index) -> fromBytes(row.getBytes("id")), toBytes(snapshot.eventId()));
		if (snapshot.rules().prizes().stream().anyMatch(prize -> !prizeIds.contains(prize.id()))) invalid();
	}

	@Override
	public boolean hasResults(UUID runId) { return results.existsByDrawRunId(runId); }

	@Override
	public List<DrawSelection> findCurrentAwards(UUID eventId) {
		return jdbc.query("""
			select r.event_prize_id, prize.prize_rank, r.slot_number, r.selection_order, r.candidate_id, p.user_id
			from draw_results r join draw_runs run on run.id = r.draw_run_id
			join draw_candidates c on c.id = r.candidate_id
			join event_participants p on p.id = c.participant_id
			join event_prizes prize on prize.id = r.event_prize_id
			where run.event_id = ? and run.status = 'CONFIRMED' and r.result_type = 'SELECTED'
			and not exists (select 1 from award_cancellations cancellation where cancellation.draw_result_id = r.id)
			""", (row, index) -> new DrawSelection(fromBytes(row.getBytes("event_prize_id")), row.getInt("prize_rank"),
				row.getInt("slot_number"), row.getInt("selection_order"), fromBytes(row.getBytes("candidate_id")),
				fromBytes(row.getBytes("user_id")), DrawSelectionResultType.SELECTED), toBytes(eventId));
	}

	@Override
	public DrawConfirmation findConfirmed(DrawSnapshot snapshot) {
		var run = runs.findById(snapshot.runId()).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (run.getStatus() != DrawRunStatus.CONFIRMED || run.getConfirmedAt() == null) invalid();
		List<DrawResult> stored = results.findByDrawRunId(snapshot.runId()).stream().map(result -> mapper.toDomain(result, snapshot))
				.sorted(Comparator.comparingInt((DrawResult result) -> result.getSelection().getRank())
						.thenComparingInt(result -> result.getSelection().getSlotNumber())).toList();
		return new DrawConfirmation(run.getId(), run.getEventId(), run.getConfirmedAt(), stored);
	}

	@Override
	public DrawConfirmation saveConfirmed(DrawSnapshot snapshot, List<DrawSelection> selections, Instant confirmedAt) {
		var run = runs.findById(snapshot.runId()).orElseThrow(() -> new BusinessException(INVALID_RUN));
		List<DrawResultEntity> saved = results.saveAllAndFlush(selections.stream()
				.map(selection -> new DrawResultEntity(run, selection)).toList());
		run.confirm(confirmedAt);
		runs.flush();
		// 첫 응답도 실제 DATETIME(6) 값으로 반환한다.
		entityManager.refresh(run);
		// 불변 엔티티의 INSERT 잠금 모드로 refresh하지 않고 실제 저장값을 다시 조회한다.
		saved.forEach(entityManager::detach);
		return findConfirmed(snapshot);
	}

	private static void invalid() { throw new BusinessException(INVALID_RUN); }
}
