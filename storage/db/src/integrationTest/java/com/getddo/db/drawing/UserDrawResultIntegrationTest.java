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

import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawDisplayStatus;
import com.getddo.core.drawing.domain.MyDrawResultStatus;
import com.getddo.core.drawing.domain.UserDrawResult;
import com.getddo.core.drawing.service.DrawConfirmationService;
import com.getddo.core.drawing.service.DrawPublicationService;
import com.getddo.core.drawing.service.DrawSnapshotService;
import com.getddo.core.drawing.service.UserDrawResultService;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.db.ticket.MutableClock;
import com.getddo.db.support.UserDrawResultTestConfiguration;

import static com.getddo.db.common.util.UuidBinary.toBytes;
import static org.assertj.core.api.Assertions.assertThat;

/** 기존 공개 서비스로 조회 연동을 검증한다. GD-199의 새 수동 공개 흐름 검증은 별도로 남긴다. */
@SpringBootTest(classes = {DrawSnapshotIntegrationTest.TestApplication.class, UserDrawResultTestConfiguration.class},
		properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"})
class UserDrawResultIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MutableClock clock;
	@Autowired private DrawSnapshotService snapshots;
	@Autowired private DrawConfirmationService confirmations;
	@Autowired private DrawPublicationService publications;
	@Autowired private UserDrawResultService queries;
	private UUID eventId;
	private UUID userId;
	private UUID prizeId;
	private UUID participantId;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		eventId = UUID.randomUUID(); userId = UUID.randomUUID(); prizeId = UUID.randomUUID(); participantId = UUID.randomUUID();
		jdbc.update("""
			insert into users (id, name, phone_num, role, status, membership, created_at, updated_at)
			values (?, '남궁민수', ?, 'USER', 'ACTIVE', 'VIP', ?, ?)
			""", toBytes(userId), "010" + String.format("%08d", Math.floorMod(userId.hashCode(), 100000000)), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into events (id, title, description, event_type, weighting_enabled, starts_at, ends_at,
			status, membership_rule, created_at, updated_at)
			values (?, '결과 조회', '설명', 'NO_TICKET', false, ?, ?, 'CLOSED', 'vip', ?, ?)
			""", toBytes(eventId), utc(NOW.minusSeconds(3600)), utc(NOW.minusSeconds(300)), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into event_prizes (id, event_id, prize_rank, name, winner_count, created_at, updated_at)
			values (?, ?, 1, '경품', 2, ?, ?)
			""", toBytes(prizeId), toBytes(eventId), utc(NOW), utc(NOW));
	}

	@Test
	@DisplayName("실제 공개 전에는 확정 결과를 숨기고 공개 후 명단과 본인 결과를 반환한다")
	void confirmedResultsStayHiddenUntilActualPublication() {
		// given
		enter();
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		confirmations.confirm(snapshot.runId());
		// when / then
		assertThat(queries.publicResults(user(), eventId).getPrizes()).isEmpty();
		assertThat(queries.myResult(user(), eventId).getResult()).isEqualTo(MyDrawResultStatus.PENDING);
		assertThat(queries.myResult(user(), eventId).getPrize()).isNull();
		publications.publishInitial(eventId).orElseThrow();
		UserDrawResult.PublicResults published = queries.publicResults(user(), eventId);
		assertThat(published.isPublished()).isTrue();
		assertThat(published.getRevision()).isEqualTo(1);
		assertThat(published.getPrizes().getFirst().getUnfilledCount()).isEqualTo(1);
		assertThat(published.getPrizes().getFirst().getWinners().getFirst().getMaskedName()).isEqualTo("남**수");
		assertThat(published.getPrizes().getFirst().getWinners().getFirst().getMaskedPhoneNum()).hasSize(4);
		assertThat(queries.myResult(user(), eventId).getResult()).isEqualTo(MyDrawResultStatus.WON);
		assertThat(queries.myResult(user(), eventId).getPrize().getId()).isEqualTo(prizeId);
		jdbc.update("update events set status = 'REDRAWING' where id = ?", toBytes(eventId));
		assertThat(queries.publicResults(user(), eventId).getDisplayStatus()).isEqualTo(DrawDisplayStatus.PUBLISHED);
	}

	/** 새 공개 버전의 빈 명단을 저장한 fixture로 과거 버전이나 내부 확정 명단을 읽지 않는지 확인한다. */
	@Test
	@DisplayName("최신 공개 버전의 빈 명단을 과거 당첨 결과로 대체하지 않는다")
	void latestPublicationDoesNotFallBackToEarlierWinner() {
		// given
		enter();
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		confirmations.confirm(snapshot.runId());
		publications.publishInitial(eventId).orElseThrow();
		clock.set(NOW.plusSeconds(60));
		jdbc.update("""
			insert into draw_publications (id, event_id, source_draw_id, revision, publication_type, reason,
			confirmed_by, confirmed_at, published_at) values (?, ?, ?, 2, 'UPDATE', '검증 fixture', ?, ?, ?)
			""", toBytes(UUID.randomUUID()), toBytes(eventId), toBytes(snapshot.runId()), toBytes(userId),
				utc(NOW.plusSeconds(60)), utc(NOW.plusSeconds(60)));
		UserDrawResult.PublicResults result = queries.publicResults(user(), eventId);
		// when / then
		assertThat(result.getRevision()).isEqualTo(2);
		assertThat(result.getPublishedAt()).isEqualTo(NOW);
		assertThat(result.getUpdatedAt()).isEqualTo(NOW.plusSeconds(60));
		assertThat(result.getPrizes().getFirst().getWinners()).isEmpty();
		assertThat(result.getPrizes().getFirst().getUnfilledCount()).isEqualTo(2);
		assertThat(queries.myResult(user(), eventId).getResult()).isEqualTo(MyDrawResultStatus.LOST);
	}

	@Test
	@DisplayName("거절 이력은 응모로 보지 않고 응모자 없음 종료를 표시한다")
	void rejectedHistoryIsNotEntryAndNoEntriesRunIsVisible() {
		// given
		jdbc.update("""
			insert into event_entries (id, event_id, user_id, status, rejection_code, rejection_reason,
			requested_ticket_count, deducted_ticket_count, created_at)
			values (?, ?, ?, 'REJECTED', 'ENTRY-005', '마감', 0, 0, ?)
			""", toBytes(UUID.randomUUID()), toBytes(eventId), toBytes(userId), utc(NOW));
		snapshots.prepareInitial(eventId);
		// when / then
		assertThat(queries.myResult(user(), eventId).getResult()).isEqualTo(MyDrawResultStatus.NOT_ENTERED);
		assertThat(queries.publicResults(user(), eventId).getDisplayStatus()).isEqualTo(DrawDisplayStatus.NO_ENTRANTS);
		assertThat(queries.publicResults(user(), eventId).isPublished()).isFalse();
	}

	@Test
	@DisplayName("취소 이벤트 응모자의 경품은 공개하지 않는다")
	void canceledEntryDoesNotRevealPrize() {
		// given
		enter();
		jdbc.update("update events set status = 'CANCELED', canceled_at = ? where id = ?", utc(NOW), toBytes(eventId));
		// when / then
		assertThat(queries.publicResults(user(), eventId).getDisplayStatus()).isEqualTo(DrawDisplayStatus.CANCELED);
		assertThat(queries.myResult(user(), eventId).getResult()).isEqualTo(MyDrawResultStatus.CANCELED);
		assertThat(queries.myResult(user(), eventId).getPrize()).isNull();
	}

	private void enter() {
		jdbc.update("insert into event_participants (id, event_id, user_id, used_ticket_count, created_at) values (?, ?, ?, 0, ?)",
				toBytes(participantId), toBytes(eventId), toBytes(userId), utc(NOW.minusSeconds(301)));
		jdbc.update("""
			insert into event_entries (id, event_id, participant_id, user_id, requested_ticket_count, deducted_ticket_count, created_at)
			values (?, ?, ?, ?, 0, 0, ?)
			""", toBytes(UUID.randomUUID()), toBytes(eventId), toBytes(participantId), toBytes(userId), utc(NOW.minusSeconds(301)));
	}

	private User user() { return new User(userId, "남궁민수", UserRole.USER, UserStatus.ACTIVE, null, null, null, null, null, NOW, NOW); }
	private static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }

}
