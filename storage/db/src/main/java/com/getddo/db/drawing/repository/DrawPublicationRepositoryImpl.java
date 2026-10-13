package com.getddo.db.drawing.repository;

import java.time.Instant;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;

import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawPublicationExecution;
import com.getddo.db.drawing.entity.DrawPublicationExecutionEntity;
import com.getddo.core.drawing.domain.DrawPublishedSlot;
import com.getddo.core.drawing.domain.DrawPublicationRun;
import com.getddo.core.drawing.domain.DrawPublicationCancellation;
import com.getddo.core.drawing.domain.DrawResultConfirmation;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawResult;
import com.getddo.core.drawing.domain.DrawSelection;
import com.getddo.core.drawing.domain.DrawSelectionResultType;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.db.drawing.entity.DrawPublicationEntity;
import com.getddo.db.drawing.entity.DrawPublicationResultEntity;
import com.getddo.db.drawing.entity.DrawResultConfirmationEntity;

import static com.getddo.db.common.util.UuidBinary.toBytes;
import static com.getddo.db.common.util.UuidBinary.fromBytes;
import static com.getddo.core.drawing.exception.DrawPublicationErrorCode.INVALID_PUBLICATION;

/** 공개 전용 JPA 저장. 선정·취소·재추첨 등록의 공용 파일을 변경하지 않는다. */
@Repository
@RequiredArgsConstructor
public class DrawPublicationRepositoryImpl implements DrawPublicationRepository {
	private final DrawPublicationJpaRepository publications;
	private final DrawPublicationResultJpaRepository entries;
	private final EntityManager entityManager;
	private final JdbcTemplate jdbc;
	private final DrawResultConfirmationJpaRepository confirmations;
	private final DrawPublicationExecutionJpaRepository executionEvidence;

	/** 취소 근거는 실행 등록 시 확정된 award_cancellations의 불변 필드를 통해 조회한다. */
	@Override
	public void saveExecutionEvidence(UUID publicationId, UUID eventId, List<DrawPublicationExecution> executions) {
		if (executions.isEmpty()) return;
		executionEvidence.saveAllAndFlush(executions.stream()
				.map(value -> new DrawPublicationExecutionEntity(publicationId, eventId, value)).toList());
	}

	/** 고정된 버전 연결과 수정하지 않는 확인·취소 근거만 읽는다. */
	@Override
	public Map<UUID, List<DrawPublicationExecution>> findExecutionEvidence(Set<UUID> publicationIds) {
		if (publicationIds.isEmpty()) return Map.of();
		String placeholders = String.join(",", Collections.nCopies(publicationIds.size(), "?"));
		String sql = """
			select link.publication_id, link.draw_run_id, checked.id confirmation_id, checked.confirmed_by, checked.confirmed_at
			from draw_publication_executions link
			join draw_result_confirmations checked on checked.id = link.confirmation_id and checked.draw_run_id = link.draw_run_id
			join draw_runs run on run.id = link.draw_run_id
			where link.publication_id in (
			""" + placeholders + ") order by link.publication_id, run.run_number";
		List<EvidenceRow> rows = jdbc.query(sql, (row, index) -> new EvidenceRow(fromBytes(row.getBytes("publication_id")),
				fromBytes(row.getBytes("draw_run_id")), fromBytes(row.getBytes("confirmation_id")),
				fromBytes(row.getBytes("confirmed_by")), instant(row, "confirmed_at")),
				publicationIds.stream().map(id -> toBytes(id)).toArray());
		Set<UUID> runIds = new HashSet<>();
		for (EvidenceRow row : rows) runIds.add(row.runId());
		Map<UUID, List<DrawPublicationCancellation>> causes = new HashMap<>();
		if (!runIds.isEmpty()) {
			String cancellationSql = "select id, draw_result_id, draw_run_id, reason from award_cancellations where draw_run_id in ("
					+ String.join(",", Collections.nCopies(runIds.size(), "?")) + ") order by canceled_at, id";
			jdbc.query(cancellationSql, (RowCallbackHandler) row -> {
				UUID runId = fromBytes(row.getBytes("draw_run_id"));
				causes.computeIfAbsent(runId, ignored -> new ArrayList<>()).add(new DrawPublicationCancellation(
						fromBytes(row.getBytes("id")), fromBytes(row.getBytes("draw_result_id")), runId, row.getString("reason")));
			}, runIds.stream().map(id -> toBytes(id)).toArray());
		}
		Map<UUID, List<DrawPublicationExecution>> loaded = new HashMap<>();
		for (EvidenceRow row : rows) loaded.computeIfAbsent(row.publicationId(), ignored -> new ArrayList<>()).add(
				new DrawPublicationExecution(row.runId(), row.confirmationId(), row.confirmedBy(), row.confirmedAt(),
						causes.getOrDefault(row.runId(), List.of())));

		return immutableCopy(loaded);
	}

	private static <T> Map<UUID, List<T>> immutableCopy(Map<UUID, List<T>> loaded) {
		Map<UUID, List<T>> immutable = new HashMap<>();
		loaded.forEach((id, values) -> immutable.put(id, List.copyOf(values)));
		return Map.copyOf(immutable);
	}

	private record EvidenceRow(UUID publicationId, UUID runId, UUID confirmationId, UUID confirmedBy, Instant confirmedAt) { }

	@Override
	public Optional<DrawPublication> findLatest(UUID eventId) {
		return publications.findFirstByEventIdOrderByRevisionDesc(eventId).map(this::toDomain);
	}

	@Override
	public List<DrawPublication> findHistory(UUID eventId) {
		return publications.findByEventIdOrderByRevisionAsc(eventId).stream().map(this::toDomain).toList();
	}

	@Override
	public PageResult<DrawPublication> findHistory(UUID eventId, PageQuery query) {
		long total = publications.countByEventId(eventId);
		if (query.offset() >= total) return new PageResult<>(List.of(), query.getPage(), query.getSize(), total);
		List<DrawPublicationEntity> stored = publications.findByEventIdOrderByRevisionDesc(eventId,
				PageRequest.of(query.getPage() - 1, query.getSize()));
		Set<UUID> ids = new HashSet<>();
		for (DrawPublicationEntity publication : stored) ids.add(publication.getId());
		Map<UUID, List<DrawPublication.Entry>> included = new HashMap<>();
		if (!ids.isEmpty()) {
			for (DrawPublicationResultEntity entry : entries.findByIdPublicationIdIn(ids)) {
				included.computeIfAbsent(entry.getId().getPublicationId(), ignored -> new ArrayList<>()).add(entry.toDomain());
			}
		}
		included.values().forEach(rows -> rows.sort(Comparator.comparing(DrawPublication.Entry::resultId)));
		List<DrawPublication> page = stored.stream().map(publication -> publication.toDomain(
				included.getOrDefault(publication.getId(), List.of()))).toList();
		return new PageResult<>(page, query.getPage(), query.getSize(), total);
	}

	@Override
	public Optional<DrawPublication> findPrevious(UUID eventId, int revision) {
		return publications.findFirstByEventIdAndRevisionLessThanOrderByRevisionDesc(eventId, revision)
				.map(this::toDomain);
	}

	@Override
	public boolean eventExists(UUID eventId) {
		Integer exists = jdbc.queryForObject("select exists(select 1 from events where id = ?)", Integer.class, toBytes(eventId));
		return exists != null && exists == 1;
	}

	@Override
	public boolean wasPublished(UUID resultId) { return entries.existsByIdResultId(resultId); }

	/** 실제 포함 결과만 조인한다. 취소 여부·현재 유효 배정을 과거 공개 여부로 해석하지 않는다. */
	@Override
	public List<DrawPublishedSlot> findPublishedSlots(UUID publicationId) {
		return jdbc.query("""
			select r.event_prize_id, r.slot_number, r.id, participant.user_id
			from draw_publication_results published join draw_results r on r.id = published.draw_result_id
			join draw_candidates candidate on candidate.id = r.candidate_id
			join event_participants participant on participant.id = candidate.participant_id
			where published.publication_id = ?
			order by r.event_prize_id, r.slot_number
			""", (row, index) -> new DrawPublishedSlot(fromBytes(row.getBytes("event_prize_id")),
				row.getInt("slot_number"), fromBytes(row.getBytes("id")), fromBytes(row.getBytes("user_id"))),
				toBytes(publicationId));
	}

	/** 페이지의 포함 결과와 경계 이전 버전을 한 번에 조회한다. UUID 값은 모두 바인딩한다. */
	@Override
	public Map<UUID, List<DrawPublishedSlot>> findPublishedSlots(Set<UUID> publicationIds) {
		if (publicationIds.isEmpty()) return Map.of();
		Map<UUID, List<DrawPublishedSlot>> loaded = new HashMap<>();
		for (UUID id : publicationIds) loaded.put(id, new ArrayList<>());
		String placeholders = String.join(",", Collections.nCopies(publicationIds.size(), "?"));
		String sql = """
			select published.publication_id, r.event_prize_id, r.slot_number, r.id, participant.user_id
			from draw_publication_results published join draw_results r on r.id = published.draw_result_id
			join draw_candidates candidate on candidate.id = r.candidate_id
			join event_participants participant on participant.id = candidate.participant_id
			where published.publication_id in (
			""" + placeholders + ") order by published.publication_id, r.event_prize_id, r.slot_number";
		jdbc.query(sql, (RowCallbackHandler) row -> loaded.get(fromBytes(row.getBytes("publication_id"))).add(
				new DrawPublishedSlot(fromBytes(row.getBytes("event_prize_id")), row.getInt("slot_number"),
						fromBytes(row.getBytes("id")), fromBytes(row.getBytes("user_id")))),
				publicationIds.stream().map(id -> toBytes(id)).toArray());
		return immutableCopy(loaded);
	}

	@Override
	public boolean hasCancellationOrReplacement(UUID eventId) {
		Integer count = jdbc.queryForObject("""
			select count(*) from draw_runs run where run.event_id = ? and
			(run.draw_type = 'REPLACEMENT' or exists (
			  select 1 from award_cancellations c join draw_results r on r.id = c.draw_result_id
			  where r.draw_run_id = run.id))
			""", Integer.class, toBytes(eventId));
		return count != null && count > 0;
	}

	@Override
	public Optional<UUID> findEventId(UUID runId) {
		return jdbc.query("select event_id from draw_runs where id = ?",
				(row, index) -> fromBytes(row.getBytes("event_id")), toBytes(runId)).stream().findFirst();
	}

	@Override
	public List<DrawPublicationRun> findReplacementRuns(UUID eventId) {
		List<ReplacementRunRow> runs = jdbc.query("""
			select id, event_id, run_number, status, confirmed_at from draw_runs
			where event_id = ? and draw_type = 'REPLACEMENT' order by run_number
			""", (row, index) -> new ReplacementRunRow(fromBytes(row.getBytes("id")), fromBytes(row.getBytes("event_id")),
					row.getInt("run_number"), DrawRunStatus.valueOf(row.getString("status")), instant(row, "confirmed_at")), toBytes(eventId));
		if (runs.isEmpty()) return List.of();
		Set<UUID> runIds = new HashSet<>();
		for (ReplacementRunRow run : runs) runIds.add(run.id());
		Map<UUID, List<DrawResult>> results = findResults(runIds, eventId);
		Map<UUID, DrawResultConfirmation> checked = new HashMap<>();
		for (DrawResultConfirmationEntity confirmation : confirmations.findByRunIdIn(runIds)) {
			DrawResultConfirmation value = confirmation.toDomain();
			checked.put(value.runId(), value);
		}
		return runs.stream().map(run -> new DrawPublicationRun(run.id(), run.eventId(), run.number(), run.status(),
				run.confirmedAt(), results.getOrDefault(run.id(), List.of()), checked.get(run.id()))).toList();
	}

	/** JDBC 조회의 실행 메타데이터. 확정 결과·확인 기록은 실행 ID로 일괄 결합한다. */
	private record ReplacementRunRow(UUID id, UUID eventId, int number, DrawRunStatus status, Instant confirmedAt) { }

	@Override
	public List<DrawPublicationCancellation> findCancellations(UUID eventId) {
		return jdbc.query("""
			select c.id, c.draw_result_id, c.draw_run_id, c.reason from award_cancellations c
			join draw_results result on result.id = c.draw_result_id
			join draw_runs origin on origin.id = result.draw_run_id
			where origin.event_id = ? order by c.canceled_at, c.id
			""", (row, index) -> new DrawPublicationCancellation(fromBytes(row.getBytes("id")),
				fromBytes(row.getBytes("draw_result_id")), nullableUuid(row, "draw_run_id"), row.getString("reason")),
				toBytes(eventId));
	}

	/** 이벤트 선잠금 안에서만 호출한다. UNIQUE와 서비스의 재조회로 확인자를 덮어쓰지 않는다. */
	@Override
	public DrawResultConfirmation saveConfirmation(UUID runId, UUID actorId, Instant confirmedAt) {
		DrawResultConfirmationEntity saved = confirmations.saveAndFlush(
				new DrawResultConfirmationEntity(runId, actorId, confirmedAt));
		UUID id = saved.getId();
		entityManager.detach(saved);
		return confirmations.findById(id).orElseThrow().toDomain();
	}

	@Override
	public DrawPublication savePublication(UUID eventId, UUID sourceRunId, int revision, String reason,
			DrawResultConfirmation confirmation, List<DrawPublication.Entry> included, Instant publishedAt) {
		included = DrawPublication.validatedEntries(included);
		DrawPublicationEntity publication = new DrawPublicationEntity(eventId, sourceRunId, revision, reason,
				confirmation == null ? null : confirmation.confirmedBy(),
				confirmation == null ? null : confirmation.confirmedAt(), publishedAt);
		DrawPublicationEntity saved = publications.saveAndFlush(publication);
		entries.saveAllAndFlush(included.stream()
				.map(entry -> new DrawPublicationResultEntity(saved.getId(), eventId, entry)).toList());
		UUID id = saved.getId();
		entityManager.detach(saved);
		return toDomain(publications.findById(id).orElseThrow());
	}

	/** GD-87의 확정 결과를 읽으며 경품·원본 후보·실행 후보 연결의 이벤트 일치를 대조한다. */
	private Map<UUID, List<DrawResult>> findResults(Set<UUID> runIds, UUID eventId) {
		Map<UUID, List<DrawResult>> results = new HashMap<>();
		for (UUID runId : runIds) results.put(runId, new ArrayList<>());
		String placeholders = String.join(",", Collections.nCopies(runIds.size(), "?"));
		String sql = """
			select r.draw_run_id, r.id, r.event_prize_id, r.slot_number, r.selection_order, r.candidate_id, r.result_type,
			r.created_at, prize.prize_rank, prize.event_id as prize_event, p.user_id,
			p.event_id as participant_event, origin.event_id as candidate_event, link.candidate_id as linked_candidate
			from draw_results r join event_prizes prize on prize.id = r.event_prize_id
			left join draw_candidates c on c.id = r.candidate_id
			left join event_participants p on p.id = c.participant_id
			left join draw_runs origin on origin.id = c.draw_run_id
			left join draw_run_candidates link on link.draw_run_id = r.draw_run_id and link.candidate_id = r.candidate_id
			where r.draw_run_id in (
			""" + placeholders + ") order by r.draw_run_id, prize.prize_rank, r.slot_number";
		jdbc.query(sql, (RowCallbackHandler) row -> {
			results.get(fromBytes(row.getBytes("draw_run_id"))).add(readResult(row, eventId));
		}, runIds.stream().map(id -> toBytes(id)).toArray());
		Map<UUID, List<DrawResult>> immutable = new HashMap<>();
		results.forEach((id, rows) -> immutable.put(id, List.copyOf(rows)));
		return Map.copyOf(immutable);
	}

	private DrawResult readResult(ResultSet row, UUID eventId) throws SQLException {
		DrawSelectionResultType type = DrawSelectionResultType.valueOf(row.getString("result_type"));
		if (!eventId.equals(nullableUuid(row, "prize_event")) || (type == DrawSelectionResultType.SELECTED
				&& (!eventId.equals(nullableUuid(row, "participant_event"))
				|| !eventId.equals(nullableUuid(row, "candidate_event")) || nullableUuid(row, "linked_candidate") == null))) {
			throw new BusinessException(INVALID_PUBLICATION);
		}
		Integer order = row.getObject("selection_order", Integer.class);
		DrawSelection selection = new DrawSelection(fromBytes(row.getBytes("event_prize_id")),
				row.getInt("prize_rank"), row.getInt("slot_number"), order, nullableUuid(row, "candidate_id"),
				nullableUuid(row, "user_id"), type);
		return new DrawResult(fromBytes(row.getBytes("id")), selection, instant(row, "created_at"));
	}

	private static UUID nullableUuid(ResultSet row, String column) throws SQLException {
		byte[] bytes = row.getBytes(column);
		return bytes == null ? null : fromBytes(bytes);
	}
	private static Instant instant(ResultSet row, String column) throws SQLException {
		java.time.LocalDateTime value = row.getObject(column, java.time.LocalDateTime.class);
		return value == null ? null : value.toInstant(java.time.ZoneOffset.UTC);
	}

	/** 이벤트 선잠금 아래 하나의 트랜잭션으로 저장하고 DB의 실제 시각을 반환한다. */
	@Override
	public DrawPublication saveInitial(UUID eventId, UUID runId, List<UUID> selectedResultIds, Instant publishedAt) {
		List<DrawPublication.Entry> included = selectedResultIds.stream()
				.map(resultId -> new DrawPublication.Entry(resultId, runId, null)).toList();
		DrawPublicationEntity saved = publications.saveAndFlush(new DrawPublicationEntity(eventId, runId, publishedAt));
		List<DrawPublicationResultEntity> stored = included.stream()
				.map(entry -> new DrawPublicationResultEntity(saved.getId(), eventId, entry)).toList();
		entries.saveAllAndFlush(stored);
		UUID publicationId = saved.getId();
		entityManager.detach(saved);
		return toDomain(publications.findById(publicationId).orElseThrow());
	}

	private DrawPublication toDomain(DrawPublicationEntity publication) {
		return publication.toDomain(entries.findByIdPublicationIdOrderByIdResultId(publication.getId())
				.stream().map(DrawPublicationResultEntity::toDomain).toList());
	}
}
