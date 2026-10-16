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

/**
 * 기존 응모·공개 연결 테이블에서 사용자 결과 조회에 필요한 값만 읽는다.
 * 트랜잭션은 호출한 서비스에서 관리하며, 공개 명단은 지정한 공개 버전에 포함된 결과로 한정한다.
 */
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

	/**
	 * {@inheritDoc}
	 * 읽은 이름·전화번호는 행 변환 단계에서 바로 가려 원본을 서비스에 전달하지 않는다.
	 * 공개 후 개별 취소가 기록돼도 이 버전의 명단이 바뀌지 않도록 현재 취소 상태로 거르지 않는다.
	 */
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

	/**
	 * {@inheritDoc}
	 * 공개 버전과 응모자의 사용자 식별자를 함께 제한해 다른 사용자의 당첨 경품이 섞이지 않게 한다.
	 */
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
