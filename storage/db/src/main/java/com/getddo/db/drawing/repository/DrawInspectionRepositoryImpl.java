package com.getddo.db.drawing.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.DrawInspectionRepository;
import com.getddo.db.drawing.mapper.DrawInspectionRulesMapper;

import static com.getddo.db.common.util.UuidBinary.fromBytes;
import static com.getddo.db.common.util.UuidBinary.toBytes;

/** 저장 행을 읽기만 한다. 업무 트랜잭션과 관리자 권한은 Service에서 관리한다. */
@Repository
@RequiredArgsConstructor
public class DrawInspectionRepositoryImpl implements DrawInspectionRepository {
	private final JdbcTemplate jdbc;
	private final DrawInspectionRulesMapper rulesMapper;
	private final JsonMapper json = JsonMapper.builder().build();
	private static final String RUNS = """
		select run.*, original.id as original_id from draw_runs run
		left join draw_runs original on original.event_id = run.event_id and original.draw_type = 'INITIAL'
		""";
	private static final String CANDIDATES = """
		select c.*, origin.event_id as source_event_id, p.event_id as participant_event_id, p.user_id
		from draw_run_candidates link join draw_candidates c on c.id = link.candidate_id
		join draw_runs origin on origin.id = c.draw_run_id
		join event_participants p on p.id = c.participant_id
		where link.draw_run_id = ?
		""";

	@Override
	public boolean eventExists(UUID eventId) {
		return jdbc.queryForObject("select count(*) from events where id = ?", Long.class, toBytes(eventId)) > 0;
	}

	@Override
	public Optional<DrawRunView> findRun(UUID runId) {
		return jdbc.query(RUNS + " where run.id = ?", this::run, toBytes(runId)).stream().findFirst();
	}

	@Override
	public PageResult<DrawRunView> findRuns(UUID eventId, PageQuery page) {
		long count = jdbc.queryForObject("select count(*) from draw_runs where event_id = ?", Long.class, toBytes(eventId));
		List<DrawRunView> items = jdbc.query(RUNS + " where run.event_id = ? order by run.run_number desc limit ? offset ?",
				this::run, toBytes(eventId), page.getSize(), page.offset());
		return new PageResult<>(items, page.getPage(), page.getSize(), count);
	}

	@Override
	public PageResult<DrawCandidateView> findCandidates(UUID runId, PageQuery page) {
		long count = jdbc.queryForObject("select count(*) from draw_run_candidates where draw_run_id = ?", Long.class, toBytes(runId));
		List<DrawCandidateView> items = jdbc.query(CANDIDATES + " order by c.id limit ? offset ?",
				this::candidate, toBytes(runId), page.getSize(), page.offset());
		return new PageResult<>(items, page.getPage(), page.getSize(), count);
	}

	@Override
	public List<DrawCandidateView> findAllCandidates(UUID runId) {
		return jdbc.query(CANDIDATES + " order by c.id", this::candidate, toBytes(runId));
	}

	@Override
	public List<DrawResultView> findResults(UUID runId) {
		return jdbc.query("""
			select result.*, prize.event_id as prize_event_id, participant.user_id,
			exists(select 1 from draw_publication_results published where published.draw_result_id = result.id) as was_published,
			exists(select 1 from award_cancellations canceled where canceled.draw_result_id = result.id) as is_canceled
			from draw_results result join event_prizes prize on prize.id = result.event_prize_id
			left join draw_candidates candidate on candidate.id = result.candidate_id
			left join event_participants participant on participant.id = candidate.participant_id
			where result.draw_run_id = ? order by prize.prize_rank, result.slot_number
			""", (row, index) -> new DrawResultView(id(row, "id"), id(row, "draw_run_id"), id(row, "event_prize_id"),
				id(row, "prize_event_id"), row.getInt("slot_number"), row.getObject("selection_order", Integer.class),
				DrawSelectionResultType.valueOf(row.getString("result_type")), id(row, "candidate_id"), id(row, "user_id"),
				row.getBoolean("was_published"), row.getBoolean("is_canceled")), toBytes(runId));
	}

	/** 현재 취소 상태를 필터링하지 않고, 검증 대상보다 이전 차수에서 확정한 배정을 조회한다. */
	@Override
	public DrawVerificationReferences findReplacementReferences(DrawRunView run) {
		if (run.getOriginalDrawId() == null) return DrawVerificationReferences.empty();
		DrawRunView original = findRun(run.getOriginalDrawId()).orElse(null);
		List<DrawCandidateView> candidates = original == null ? List.of() : findAllCandidates(original.getId());
		if (run.getSnapshotFixedAt() == null) return new DrawVerificationReferences(original, candidates, List.of());
		List<DrawHistoricalAward> awards = jdbc.query("""
			select result.id, result.draw_run_id, run.event_id, result.event_prize_id, prize.event_id as prize_event_id,
			result.slot_number, result.candidate_id, participant.user_id, run.confirmed_at
			from draw_results result join draw_runs run on run.id = result.draw_run_id
			join event_prizes prize on prize.id = result.event_prize_id
			join draw_candidates candidate on candidate.id = result.candidate_id
			join event_participants participant on participant.id = candidate.participant_id
			where run.event_id = ? and run.run_number < ? and run.confirmed_at <= ?
			and run.status = 'CONFIRMED' and result.result_type = 'SELECTED'
			order by run.run_number, result.id
			""", (row, index) -> new DrawHistoricalAward(id(row, "id"), id(row, "draw_run_id"), id(row, "event_id"),
				id(row, "event_prize_id"), id(row, "prize_event_id"), row.getInt("slot_number"), id(row, "candidate_id"),
				id(row, "user_id"), instant(row, "confirmed_at")), toBytes(run.getEventId()), run.getRunNumber(),
				java.time.LocalDateTime.ofInstant(run.getSnapshotFixedAt(), java.time.ZoneOffset.UTC));
		return new DrawVerificationReferences(original, candidates, awards);
	}

	private DrawRunView run(ResultSet row, int index) throws SQLException {
		DrawType type = DrawType.valueOf(row.getString("draw_type"));
		DrawInspectionRulesMapper.Decoded decoded = rulesMapper.decode(row.getString("rules_snapshot"));
		return new DrawRunView(id(row, "id"), id(row, "event_id"), row.getInt("run_number"), type,
				DrawRunStatus.valueOf(row.getString("status")), type == DrawType.INITIAL ? null : id(row, "original_id"),
				row.getString("algorithm_version"), decoded.rules(), instant(row, "snapshot_fixed_at"),
				instant(row, "started_at"), instant(row, "confirmed_at"), instant(row, "created_at"), decoded.replacementRules());
	}

	private DrawCandidateView candidate(ResultSet row, int index) throws SQLException {
		DrawSnapshot.EligibilityEvidence eligibility = parse(row.getString("eligibility_snapshot"), DrawSnapshot.EligibilityEvidence.class);
		DrawSnapshot.Candidate snapshot = new DrawSnapshot.Candidate(id(row, "id"), id(row, "participant_id"),
				eligibility == null ? null : eligibility.userId(), row.getLong("ticket_count"), row.getLong("weight"),
				parse(row.getString("entry_snapshot"), DrawSnapshot.EntryEvidence.class), eligibility);
		return new DrawCandidateView(snapshot, id(row, "draw_run_id"), id(row, "source_event_id"),
				id(row, "participant_event_id"), id(row, "user_id"));
	}

	/** 구조가 잘못된 스냅샷도 검증 이력에 남기도록 null로 표현한다. DB 연결 장애는 전파한다. */
	private <T> T parse(String value, Class<T> type) {
		if (value == null) return null;
		try { return json.readValue(value, type); }
		catch (JacksonException invalidSnapshot) { return null; }
	}

	private static UUID id(ResultSet row, String column) throws SQLException {
		byte[] value = row.getBytes(column);
		return value == null ? null : fromBytes(value);
	}
	private static Instant instant(ResultSet row, String column) throws SQLException {
		java.time.LocalDateTime value = row.getObject(column, java.time.LocalDateTime.class);
		return value == null ? null : value.toInstant(java.time.ZoneOffset.UTC);
	}
}
