package com.getddo.db.drawing;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.drawing.domain.AwardCancellation;
import com.getddo.core.drawing.domain.DrawConfirmation;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.service.DrawConfirmationService;
import com.getddo.core.drawing.service.DrawSnapshotService;
import com.getddo.db.drawing.repository.AwardCancellationRepositoryImpl;
import com.getddo.db.drawing.repository.DrawResultRepositoryImpl;
import com.getddo.db.ticket.MutableClock;

import static com.getddo.db.common.util.UuidBinary.toBytes;
import static org.assertj.core.api.Assertions.*;

/** 취소 영속성만 검증한다. SQL로 준비한 실행은 GD-87 실제 실행 연동을 대신하지 않는다. */
@SpringBootTest(classes = DrawSnapshotIntegrationTest.TestApplication.class,
		properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"})
class AwardCancellationIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
	@Autowired private AwardCancellationRepositoryImpl cancellations;
	@Autowired private DrawResultRepositoryImpl results;
	@Autowired private DrawSnapshotService snapshots;
	@Autowired private DrawConfirmationService confirmation;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MutableClock clock;
	private UUID actorId;
	private UUID eventId;
	private UUID runId;
	private UUID winId;
	private UUID replacementId;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		actorId = UUID.randomUUID();
		eventId = UUID.randomUUID();
		replacementId = UUID.randomUUID();
		UUID participantId = UUID.randomUUID();
		jdbc.update("""
			insert into users (id, name, role, status, membership, created_at, updated_at)
			values (?, '취소 테스트', 'ADMIN', 'ACTIVE', 'VIP', ?, ?)
			""", toBytes(actorId), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into events (id, title, description, image_key, event_type, weighting_enabled,
			starts_at, ends_at, status, membership_rule, created_at, updated_at)
			values (?, '취소 테스트', '설명', 'test.png', 'NO_TICKET', false, ?, ?, 'CLOSED', 'vip', ?, ?)
			""", toBytes(eventId), utc(NOW.minusSeconds(3600)), utc(NOW.minusSeconds(300)), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into event_prizes (id, event_id, prize_rank, name, winner_count, created_at, updated_at)
			values (?, ?, 1, '경품', 1, ?, ?)
			""", toBytes(UUID.randomUUID()), toBytes(eventId), utc(NOW), utc(NOW));
		jdbc.update("insert into event_participants (id, event_id, user_id, used_ticket_count, created_at) values (?, ?, ?, 0, ?)",
				toBytes(participantId), toBytes(eventId), toBytes(actorId), utc(NOW.minusSeconds(301)));
		jdbc.update("""
			insert into event_entries (id, participant_id, user_id, requested_ticket_count, deducted_ticket_count, created_at)
			values (?, ?, ?, 0, 0, ?)
			""", toBytes(UUID.randomUUID()), toBytes(participantId), toBytes(actorId), utc(NOW.minusSeconds(301)));
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		runId = snapshot.runId();
		winId = confirmation.confirm(runId).getResults().getFirst().getId();
		jdbc.update("""
			insert into draw_runs (id, event_id, run_number, draw_type, status, created_at)
			values (?, ?, 1, 'REPLACEMENT', 'PREPARING', ?)
			""", toBytes(replacementId), toBytes(eventId), utc(NOW));
	}

	@Test
	@DisplayName("취소 사유·처리자·연결 실행을 JPA로 보존하고 원래 확정 결과는 유지한다")
	void persistsCancellationWithoutRewritingConfirmedResult() {
		// given
		AwardCancellation requested = cancellation(NOW.plusNanos(123456789));
		DrawConfirmation original = confirmation.confirm(runId);
		// when
		AwardCancellation stored = new TransactionTemplate(transactionManager).execute(status -> cancellations.save(requested));
		// then
		assertThat(stored).isNotNull();
		assertThat(stored.id().version()).isEqualTo(7);
		assertThat(stored.canceledAt().getNano() % 1000).isZero();
		assertThat(stored).usingRecursiveComparison().ignoringFields("canceledAt").isEqualTo(requested);
		assertThat(cancellations.findByDrawResultId(winId)).contains(stored);
		assertThat(results.findCurrentAwards(eventId)).isEmpty();
		assertThat(confirmation.confirm(runId)).usingRecursiveComparison().isEqualTo(original);
	}

	@Test
	@DisplayName("취소 flush 후 호출 트랜잭션 실패는 취소를 남기지 않는다")
	void rollsBackPersistedCancellation() {
		// given / when
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			cancellations.save(cancellation(NOW));
			throw new IllegalStateException("취소 이후 실패 주입");
		})).isInstanceOf(IllegalStateException.class);
		// then
		assertThat(cancellations.findByDrawResultId(winId)).isEmpty();
		assertThat(results.findCurrentAwards(eventId)).hasSize(1);
	}

	@Test
	@DisplayName("같은 당첨의 두 번째 취소 INSERT를 UNIQUE로 거절하고 첫 사유를 보존한다")
	void rejectsDuplicateCancellationWithoutOverwritingReason() {
		// given
		AwardCancellation first = new TransactionTemplate(transactionManager).execute(status -> cancellations.save(cancellation(NOW)));
		// when / then
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status ->
				cancellations.save(new AwardCancellation(UUID.randomUUID(), winId, replacementId, actorId, "다른 사유", NOW))))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThat(cancellations.findByDrawResultId(winId)).contains(first);
	}

	private AwardCancellation cancellation(Instant canceledAt) {
		return new AwardCancellation(cancellations.nextId(), winId, replacementId, actorId, "관리자 사유", canceledAt);
	}

	private static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
}
