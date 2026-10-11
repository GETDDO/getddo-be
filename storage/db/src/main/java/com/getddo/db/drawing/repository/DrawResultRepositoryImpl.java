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
import com.getddo.db.drawing.entity.DrawRunEntity;
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

	/** 이벤트 선잠금에 필요한 소속 ID만 조회한다. 트랜잭션의 시작·종료는 확정 서비스가 담당한다. */
	@Override
	public Optional<UUID> findEventId(UUID runId) {
		return jdbc.query("select event_id from draw_runs where id = ?",
				(row, index) -> fromBytes(row.getBytes("event_id")), toBytes(runId)).stream().findFirst();
	}

	/** 서비스의 이벤트 잠금 이후 실행과 불변 입력을 읽는다. 대상 자리 계약이 없는 재추첨 실행은 거절한다. */
	@Override
	public Optional<DrawSnapshot> lockSnapshot(UUID runId) {
		return runs.findForUpdate(runId).map(run -> {
			// 재추첨의 대상 자리·유지 결과 스냅샷 계약은 GD-138에서 연결한다.
			if (run.getDrawType() != DrawType.INITIAL || run.getRunNumber() != 0) invalid();
			return snapshots.toDomain(run, links.findCandidates(runId));
		});
	}

	/** JDBC 조인으로 후보 연결·이벤트·사용자 관계를 검증하고 스냅샷 경품의 소속을 대조한다. */
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

	/** READY 실행에 저장된 부분 결과가 있는지 조회한다. 결과를 삭제하거나 보정하지 않는다. */
	@Override
	public boolean hasResults(UUID runId) { return results.existsByDrawRunId(runId); }

	/** CONFIRMED 선정 결과에서 취소된 배정을 제외한다. 원래 결과 이력과 공개 명단은 변경하지 않는다. */
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

	/** JPA 결과 행을 스냅샷 기준으로 복원하고 등수·자리 순서로 반환한다. 난수는 사용하지 않는다. */
	@Override
	public DrawConfirmation findConfirmed(DrawSnapshot snapshot) {
		DrawRunEntity run = runs.findById(snapshot.runId()).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (run.getStatus() != DrawRunStatus.CONFIRMED || run.getConfirmedAt() == null) invalid();
		List<DrawResult> stored = results.findByDrawRunId(snapshot.runId()).stream().map(result -> mapper.toDomain(result, snapshot))
				.sorted(Comparator.comparingInt((DrawResult result) -> result.getSelection().getRank())
						.thenComparingInt(result -> result.getSelection().getSlotNumber())).toList();
		return new DrawConfirmation(run.getId(), run.getEventId(), run.getConfirmedAt(), stored);
	}

	/** 서비스 트랜잭션 안에서 결과와 상태를 flush한 뒤 재조회한다. 불변 결과는 detach하여 DB 시각 정밀도를 반영한다. */
	@Override
	public DrawConfirmation saveConfirmed(DrawSnapshot snapshot, List<DrawSelection> selections, Instant confirmedAt) {
		DrawRunEntity run = runs.findById(snapshot.runId()).orElseThrow(() -> new BusinessException(INVALID_RUN));
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

	/** 실행·스냅샷 관계의 불일치를 도메인 오류로 보고하여 상위 서비스가 전체 작업을 롤백하게 한다. */
	private static void invalid() { throw new BusinessException(INVALID_RUN); }
}
