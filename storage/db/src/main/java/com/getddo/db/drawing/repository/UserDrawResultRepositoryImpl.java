package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.getddo.core.drawing.domain.UserDrawResult.MaskedWinner;
import com.getddo.core.drawing.repository.UserDrawResultRepository;

import static com.getddo.db.common.util.UuidBinary.fromBytes;
import static com.getddo.db.common.util.UuidBinary.toBytes;

/** 공개 버전에 포함된 결과를 읽는다. 이후 취소 상태로 공개 명단을 바꾸지 않는다. */
@Repository
@RequiredArgsConstructor
public class UserDrawResultRepositoryImpl implements UserDrawResultRepository {
	private final JdbcTemplate jdbc;

	@Override
	public boolean hasEntered(UUID eventId, UUID userId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
			select exists(select 1 from event_entries where event_id = ? and user_id = ? and status = 'ACCEPTED')
			""", Boolean.class, toBytes(eventId), toBytes(userId)));
	}

	@Override
	public boolean hasNoEntriesRun(UUID eventId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
			select exists(select 1 from draw_runs where event_id = ? and draw_type = 'INITIAL' and status = 'NO_ENTRIES')
			""", Boolean.class, toBytes(eventId)));
	}

	@Override
	public List<MaskedWinner> findWinners(UUID publicationId) {
		return jdbc.query("""
			select result.event_prize_id, u.name, u.phone_num
			from draw_publication_results published
			join draw_results result on result.id = published.draw_result_id
			join draw_candidates candidate on candidate.id = result.candidate_id
			join event_participants participant on participant.id = candidate.participant_id
			join users u on u.id = participant.user_id
			where published.publication_id = ?
			order by result.event_prize_id, result.slot_number
			""", (row, index) -> new MaskedWinner(fromBytes(row.getBytes("event_prize_id")),
				row.getString("name"), row.getString("phone_num")), toBytes(publicationId));
	}

	@Override
	public Optional<UUID> findWonPrize(UUID publicationId, UUID userId) {
		return jdbc.query("""
			select result.event_prize_id from draw_publication_results published
			join draw_results result on result.id = published.draw_result_id
			join draw_candidates candidate on candidate.id = result.candidate_id
			join event_participants participant on participant.id = candidate.participant_id
			where published.publication_id = ? and participant.user_id = ?
			""", (row, index) -> fromBytes(row.getBytes("event_prize_id")),
				toBytes(publicationId), toBytes(userId)).stream().findFirst();
	}
}
