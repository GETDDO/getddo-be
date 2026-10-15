package com.getddo.db.drawing.repository;

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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.ticket.domain.TicketGrade;

import static com.getddo.db.common.util.UuidBinary.fromBytes;
import static com.getddo.db.common.util.UuidBinary.toBytes;

/** 원본 이벤트·응모 조회와 이벤트 선잠금을 제공한다. */
@Repository
public class DrawSnapshotSourceReader {
	private final JdbcTemplate jdbc;

	public DrawSnapshotSourceReader(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** 실행 등록 여부만 확인하며 후보·경품 전체를 폴링에 적재하지 않는다. */
	public List<UUID> findInitialDue(Instant cutoff, int limit) {
		return jdbc.query("""
			select e.id from events e
			where e.deleted_at is null and e.status in ('OPEN', 'CLOSED') and e.ends_at <= ?
			and not exists (select 1 from draw_runs r
			 where r.event_id = e.id and r.draw_type = 'INITIAL' and r.run_number = 0)
			order by e.ends_at, e.id limit ?
			""", (row, index) -> fromBytes(row.getBytes("id")),
			LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC), limit);
	}

	/** 이벤트 행을 먼저 잠근 뒤 추첨에 필요한 이벤트 조건과 경품을 조회한다. */
	public Optional<DrawSnapshotSource.Event> lockEvent(UUID eventId) {
		List<DrawSnapshotSource.Event> events = jdbc.query("""
			select id, event_type, weighting_enabled, ends_at, status, deleted_at, membership_rule
			from events where id = ? for update
			""", (row, index) -> new DrawSnapshotSource.Event(fromBytes(row.getBytes("id")),
				EventType.valueOf(row.getString("event_type")), row.getBoolean("weighting_enabled"),
				instant(row, "ends_at"), EventStatus.valueOf(row.getString("status")),
				row.getObject("deleted_at") != null, row.getString("membership_rule"), List.of()), toBytes(eventId));
		if (events.isEmpty()) {
			return Optional.empty();
		}
		List<DrawSnapshotSource.Prize> prizes = jdbc.query("""
			select id, prize_rank, winner_count from event_prizes where event_id = ? order by prize_rank
			""", (row, index) -> new DrawSnapshotSource.Prize(fromBytes(row.getBytes("id")),
				row.getInt("prize_rank"), row.getInt("winner_count")), toBytes(eventId));
		DrawSnapshotSource.Event event = events.getFirst();
		return Optional.of(new DrawSnapshotSource.Event(event.id(), event.type(), event.weightingEnabled(),
				event.endsAt(), event.status(), event.deleted(), event.membershipRule(), prizes));
	}

	/** 응모자·응모·사용 이력을 일괄 조회해 후보 입력을 구성한다. */
	public List<DrawSnapshotSource.Participant> findParticipants(UUID eventId) {
		// 세 번의 일괄 조회로 응모자·응모·이력을 결합한다. 응모자별 N+1 조회를 하지 않는다.
		List<DrawSnapshotSource.Participant> participants = jdbc.query("""
			select p.id, p.user_id, p.used_ticket_count, u.membership, u.role
			from event_participants p join users u on u.id = p.user_id where p.event_id = ? order by p.id
			""", (row, index) -> new DrawSnapshotSource.Participant(fromBytes(row.getBytes("id")),
				fromBytes(row.getBytes("user_id")), row.getLong("used_ticket_count"), row.getString("membership"),
				row.getString("role"), List.of()), toBytes(eventId));
		Map<UUID, List<DrawSnapshotSource.Use>> uses = new LinkedHashMap<>();
		jdbc.query("""
			select h.event_entry_id, h.id, h.ticket_id, t.user_id, t.grade
			from ticket_histories h join tickets t on t.id = h.ticket_id
			join event_entries e on e.id = h.event_entry_id
			join event_participants p on p.id = e.participant_id
			where p.event_id = ? and h.operation_type = 'USE' and e.status = 'ACCEPTED' order by h.id
			""", (org.springframework.jdbc.core.RowCallbackHandler) row -> {
				uses.computeIfAbsent(fromBytes(row.getBytes("event_entry_id")), key -> new ArrayList<>())
						.add(new DrawSnapshotSource.Use(fromBytes(row.getBytes("id")), fromBytes(row.getBytes("ticket_id")),
								fromBytes(row.getBytes("user_id")), TicketGrade.valueOf(row.getString("grade"))));
			}, toBytes(eventId));
		Map<UUID, List<DrawSnapshotSource.Entry>> entries = new LinkedHashMap<>();
		jdbc.query("""
			select e.id, e.participant_id, e.deducted_ticket_count, e.created_at
			from event_entries e join event_participants p on p.id = e.participant_id
			where p.event_id = ? and e.status = 'ACCEPTED' order by e.id
			""", (org.springframework.jdbc.core.RowCallbackHandler) row -> {
				UUID id = fromBytes(row.getBytes("id"));
				entries.computeIfAbsent(fromBytes(row.getBytes("participant_id")), key -> new ArrayList<>())
						.add(new DrawSnapshotSource.Entry(id, row.getLong("deducted_ticket_count"),
								instant(row, "created_at"), uses.getOrDefault(id, List.of())));
			}, toBytes(eventId));
		return participants.stream().map(p -> new DrawSnapshotSource.Participant(p.id(), p.userId(),
				p.usedTicketCount(), p.membership(), p.role(), entries.getOrDefault(p.id(), List.of()))).toList();
	}

	private static Instant instant(ResultSet row, String column) throws SQLException {
		LocalDateTime value = row.getObject(column, LocalDateTime.class);
		return value == null ? null : value.toInstant(ZoneOffset.UTC);
	}
}
