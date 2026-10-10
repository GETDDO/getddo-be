package com.getddo.db.drawing.repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.ticket.domain.TicketGrade;

/** 응모·이력 테이블은 읽기만 한다. 이벤트 잠금은 service 트랜잭션 종료까지 유지한다. */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class DrawSnapshotRepositoryImpl implements DrawSnapshotRepository {
	private final JdbcTemplate jdbc;
	private final JsonMapper json = JsonMapper.builder().build();
	public DrawSnapshotRepositoryImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	@Override
	public Optional<DrawSnapshotSource.Event> lockEvent(UUID eventId) {
		var events = jdbc.query("""
			select id, event_type, weighting_enabled, ends_at, status, deleted_at, membership_rule
			from events where id = ? for update
			""", (row, index) -> new DrawSnapshotSource.Event(uuid(row.getBytes("id")),
				EventType.valueOf(row.getString("event_type")), row.getBoolean("weighting_enabled"),
				instant(row, "ends_at"), EventStatus.valueOf(row.getString("status")),
				row.getObject("deleted_at") != null, row.getString("membership_rule"), List.of()), bytes(eventId));
		if (events.isEmpty()) return Optional.empty();
		var prizes = jdbc.query("""
			select id, prize_rank, winner_count from event_prizes where event_id = ? order by prize_rank
			""", (row, index) -> new DrawSnapshotSource.Prize(uuid(row.getBytes("id")),
				row.getInt("prize_rank"), row.getInt("winner_count")), bytes(eventId));
		var event = events.getFirst();
		return Optional.of(new DrawSnapshotSource.Event(event.id(), event.type(), event.weightingEnabled(),
				event.endsAt(), event.status(), event.deleted(), event.membershipRule(), prizes));
	}

	@Override
	public Optional<DrawSnapshot> findInitial(UUID eventId) {
		var runs = jdbc.query("""
			select id, status, algorithm_version, rules_snapshot, snapshot_fixed_at
			from draw_runs where event_id = ? and draw_type = 'INITIAL' and run_number = 0
			""", (row, index) -> new DrawSnapshot(uuid(row.getBytes("id")), eventId,
				DrawRunStatus.valueOf(row.getString("status")), instant(row, "snapshot_fixed_at"),
				row.getString("algorithm_version"), row.getString("rules_snapshot") == null ? null
					: json.readValue(row.getString("rules_snapshot"), DrawSnapshot.Rules.class), List.of()), bytes(eventId));
		if (runs.isEmpty()) return Optional.empty();
		var run = runs.getFirst();
		var candidates = jdbc.query("""
			select c.id, c.participant_id, c.ticket_count, c.weight, c.entry_snapshot, c.eligibility_snapshot
			from draw_run_candidates rc join draw_candidates c on c.id = rc.candidate_id
			where rc.draw_run_id = ? order by c.participant_id
			""", (row, index) -> {
			var eligibility = json.readValue(row.getString("eligibility_snapshot"), DrawSnapshot.EligibilityEvidence.class);
			return new DrawSnapshot.Candidate(uuid(row.getBytes("id")), uuid(row.getBytes("participant_id")),
					eligibility.userId(), row.getLong("ticket_count"), row.getLong("weight"),
					json.readValue(row.getString("entry_snapshot"), DrawSnapshot.EntryEvidence.class), eligibility);
		}, bytes(run.runId()));
		return Optional.of(new DrawSnapshot(run.runId(), eventId, run.status(), run.fixedAt(),
				run.algorithmVersion(), run.rules(), candidates));
	}

	@Override
	public List<DrawSnapshotSource.Participant> findParticipants(UUID eventId) {
		// 세 번의 일괄 조회로 응모자·응모·이력을 결합한다. 응모자별 N+1 조회를 하지 않는다.
		var participants = jdbc.query("""
			select p.id, p.user_id, p.used_ticket_count, u.membership, u.role
			from event_participants p join users u on u.id = p.user_id where p.event_id = ? order by p.id
			""", (row, index) -> new DrawSnapshotSource.Participant(uuid(row.getBytes("id")),
				uuid(row.getBytes("user_id")), row.getLong("used_ticket_count"), row.getString("membership"),
				row.getString("role"), List.of()), bytes(eventId));
		Map<UUID, List<DrawSnapshotSource.Use>> uses = new LinkedHashMap<>();
		jdbc.query("""
			select h.event_entry_id, h.id, h.ticket_id, t.user_id, t.grade
			from ticket_histories h join tickets t on t.id = h.ticket_id
			join event_entries e on e.id = h.event_entry_id
			join event_participants p on p.id = e.participant_id
			where p.event_id = ? and h.operation_type = 'USE' order by h.id
			""", (org.springframework.jdbc.core.RowCallbackHandler) row -> {
			uses.computeIfAbsent(uuid(row.getBytes("event_entry_id")), key -> new ArrayList<>())
					.add(new DrawSnapshotSource.Use(uuid(row.getBytes("id")), uuid(row.getBytes("ticket_id")),
							uuid(row.getBytes("user_id")), TicketGrade.valueOf(row.getString("grade"))));
		}, bytes(eventId));
		Map<UUID, List<DrawSnapshotSource.Entry>> entries = new LinkedHashMap<>();
		jdbc.query("""
			select e.id, e.participant_id, e.deducted_ticket_count, e.created_at
			from event_entries e join event_participants p on p.id = e.participant_id
			where p.event_id = ? order by e.id
			""", (org.springframework.jdbc.core.RowCallbackHandler) row -> {
			UUID id = uuid(row.getBytes("id"));
			entries.computeIfAbsent(uuid(row.getBytes("participant_id")), key -> new ArrayList<>())
					.add(new DrawSnapshotSource.Entry(id, row.getLong("deducted_ticket_count"),
							instant(row, "created_at"), uses.getOrDefault(id, List.of())));
		}, bytes(eventId));
		return participants.stream().map(p -> new DrawSnapshotSource.Participant(p.id(), p.userId(),
				p.usedTicketCount(), p.membership(), p.role(), entries.getOrDefault(p.id(), List.of()))).toList();
	}

	@Override
	public DrawSnapshot saveInitial(DrawSnapshot snapshot) {
		UUID runId = newId();
		jdbc.update("""
			insert into draw_runs (id, event_id, run_number, draw_type, status, created_at)
			values (?, ?, 0, 'INITIAL', 'PREPARING', ?)
			""", bytes(runId), bytes(snapshot.eventId()), time(snapshot.fixedAt()));
		for (var candidate : snapshot.candidates()) {
			UUID candidateId = newId();
			jdbc.update("""
				insert into draw_candidates
				(id, participant_id, draw_run_id, ticket_count, weight, entry_snapshot, eligibility_snapshot, created_at)
				values (?, ?, ?, ?, ?, cast(? as json), cast(? as json), ?)
				""", bytes(candidateId), bytes(candidate.participantId()), bytes(runId), candidate.ticketCount(),
					candidate.weight(), json.writeValueAsString(candidate.entryEvidence()),
					json.writeValueAsString(candidate.eligibilityEvidence()), time(snapshot.fixedAt()));
			jdbc.update("insert into draw_run_candidates (draw_run_id, candidate_id) values (?, ?)",
					bytes(runId), bytes(candidateId));
		}
		jdbc.update("""
			update draw_runs set status = ?, algorithm_version = ?, rules_snapshot = cast(? as json),
			snapshot_fixed_at = ? where id = ?
			""", snapshot.status().name(), snapshot.algorithmVersion(), json.writeValueAsString(snapshot.rules()),
				time(snapshot.fixedAt()), bytes(runId));
		return findInitial(snapshot.eventId()).orElseThrow();
	}

	private static UUID newId() { return UuidVersion7Strategy.INSTANCE.generateUuid(null); }
	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}
	private static UUID uuid(byte[] value) {
		ByteBuffer buffer = ByteBuffer.wrap(value);
		return new UUID(buffer.getLong(), buffer.getLong());
	}
	private static LocalDateTime time(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
	private static Instant instant(ResultSet row, String column) throws SQLException {
		LocalDateTime value = row.getObject(column, LocalDateTime.class);
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}
}
