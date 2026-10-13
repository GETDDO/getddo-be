package com.getddo.db.drawing.repository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.getddo.core.drawing.repository.DrawPublicationNotificationRepository;
import static com.getddo.db.common.util.UuidBinary.toBytes;
import static com.getddo.db.common.util.UuidBinary.fromBytes;
/** 최초 결과 공개의 접수된 응모자 조회. 업무 트랜잭션은 서비스가 관리한다. */
@Repository
@RequiredArgsConstructor
public class DrawPublicationNotificationRepositoryImpl implements DrawPublicationNotificationRepository {
	private final JdbcTemplate jdbc;
	@Override public List<UUID> findParticipants(UUID eventId) {
		return jdbc.query("select p.user_id from event_participants p where p.event_id = ? and exists (select 1 from event_entries e where e.participant_id = p.id) order by p.user_id",
				(row, index) -> fromBytes(row.getBytes("user_id")), toBytes(eventId));
	}
}
