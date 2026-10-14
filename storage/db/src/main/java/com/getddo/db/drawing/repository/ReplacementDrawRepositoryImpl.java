package com.getddo.db.drawing.repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.CancellationTarget;
import com.getddo.core.drawing.domain.DrawResult;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSelectionResultType;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawType;
import com.getddo.core.drawing.repository.ReplacementDrawRepository;
import com.getddo.db.drawing.entity.DrawResultEntity;
import com.getddo.db.drawing.entity.DrawRunEntity;
import com.getddo.db.drawing.mapper.DrawResultMapper;

import static com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN;
import static com.getddo.db.common.util.UuidBinary.fromBytes;
import static com.getddo.db.common.util.UuidBinary.toBytes;

/** GD-138 전용 등록 경로. 공통 실행·입력 저장·결과 확정은 GD-87 구현을 재사용한다. */
@Repository
@RequiredArgsConstructor
public class ReplacementDrawRepositoryImpl implements ReplacementDrawRepository {
	private final JdbcTemplate jdbc;
	private final DrawRunJpaRepository runs;
	private final DrawResultJpaRepository results;
	private final DrawResultMapper mapper;

	@Override
	public Optional<CancellationTarget> findTarget(UUID resultId) {
		return jdbc.query("""
			select r.id, r.draw_run_id, run.event_id, run.status, r.result_type
			from draw_results r join draw_runs run on run.id = r.draw_run_id where r.id = ?
			""", (row, index) -> new CancellationTarget(fromBytes(row.getBytes("id")),
		fromBytes(row.getBytes("draw_run_id")), fromBytes(row.getBytes("event_id")),
		DrawRunStatus.valueOf(row.getString("status")), DrawSelectionResultType.valueOf(row.getString("result_type"))),
		toBytes(resultId)).stream().findFirst();
	}

	@Override
	public boolean hasPendingReplacement(UUID eventId) {
		return jdbc.queryForObject("""
			select count(*) from draw_runs where event_id = ? and draw_type = 'REPLACEMENT' and status <> 'CONFIRMED'
			""", Long.class, toBytes(eventId)) > 0;
	}

	/** 이벤트 선잠금을 보유한 같은 트랜잭션에서 차수를 부여한다. 실패 재개는 호출하지 않는다. */
	@Override
	public UUID register(UUID eventId) {
		Integer maximum = jdbc.queryForObject("select max(run_number) from draw_runs where event_id = ?",
		Integer.class, toBytes(eventId));
		if (maximum == null || maximum == Integer.MAX_VALUE) throw new BusinessException(INVALID_RUN);
		return runs.saveAndFlush(new DrawRunEntity(eventId, maximum + 1, DrawType.REPLACEMENT)).getId();
	}

	@Override
	public void markRedrawing(UUID eventId, Instant changedAt) {
		if (jdbc.update("update events set status = 'REDRAWING', updated_at = ? where id = ?",
		LocalDateTime.ofInstant(changedAt, ZoneOffset.UTC), toBytes(eventId)) != 1)
		throw new BusinessException(INVALID_RUN);
	}

	@Override
	public List<DrawResult> findCurrentResults(UUID eventId, DrawSnapshot original) {
		return readResults(eventId, original, false);
	}

	@Override
	public List<DrawResult> findCanceledResults(UUID eventId, DrawSnapshot original) {
		return readResults(eventId, original, true);
	}

	private List<DrawResult> readResults(UUID eventId, DrawSnapshot original, boolean canceled) {
		List<UUID> ids = jdbc.query("""
			select r.id from draw_results r join draw_runs run on run.id = r.draw_run_id
			where run.event_id = ? and run.status = 'CONFIRMED' and r.result_type = 'SELECTED'
			and (exists (select 1 from award_cancellations c where c.draw_result_id = r.id)) = ?
			order by run.run_number, r.selection_order
			""", (row, index) -> fromBytes(row.getBytes("id")), toBytes(eventId), canceled);
		List<DrawResultEntity> entities = results.findAllById(ids);
		if (entities.size() != ids.size()) throw new BusinessException(INVALID_RUN);
		// 과거 등수·사용자를 최초 스냅샷에서 복원하며 현재 경품 조건을 재계산하지 않는다.
		Map<UUID, DrawResultEntity> byId = entities.stream().collect(Collectors.toMap(DrawResultEntity::getId,
		entity -> entity));
		return ids.stream().map(id -> mapper.toDomain(byId.get(id), original)).toList();
	}
}
