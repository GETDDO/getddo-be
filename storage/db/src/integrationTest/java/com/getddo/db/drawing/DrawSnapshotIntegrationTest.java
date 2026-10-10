package com.getddo.db.drawing;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.repository.DrawExclusionRepository;
import com.getddo.core.drawing.service.DrawSnapshotService;
import com.getddo.db.support.MySqlTestConfiguration;
import com.getddo.db.ticket.MutableClock;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = DrawSnapshotIntegrationTest.TestApplication.class)
class DrawSnapshotIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");
	@Autowired private DrawSnapshotService service;
	@Autowired private MutableClock clock;
	@Autowired private PlatformTransactionManager transactionManager;
	@MockitoSpyBean private JdbcTemplate jdbc;
	@MockitoBean private DrawExclusionRepository exclusions;
	private UUID eventId, userId;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		eventId = UUID.randomUUID(); userId = UUID.randomUUID();
		jdbc.update("""
			insert into users (id, name, role, status, membership, created_at, updated_at)
			values (?, '추첨 테스트', 'USER', 'ACTIVE', 'VIP', ?, ?)
			""", bytes(userId), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into events (id, title, description, image_key, event_type, weighting_enabled,
			starts_at, ends_at, status, membership_rule, created_at, updated_at)
			values (?, '추첨 테스트', '설명', 'test.png', 'NO_TICKET', false, ?, ?, 'CLOSED', 'vip', ?, ?)
			""", bytes(eventId), utc(NOW.minusSeconds(3600)), utc(NOW.minusSeconds(300)), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into event_prizes (id, event_id, prize_rank, name, winner_count, created_at, updated_at)
			values (?, ?, 1, '경품', 2, ?, ?)
			""", bytes(UUID.randomUUID()), bytes(eventId), utc(NOW), utc(NOW));
		when(exclusions.findExcludedParticipantIds(eventId)).thenReturn(Set.of());
	}

	@Test
	void storesAndReplaysEvidenceWithoutRecomputingOriginals() {
		UUID participant = addParticipant(0);
		var first = service.prepareInitial(eventId);
		assertThat(first.status()).isEqualTo(DrawRunStatus.READY);
		assertThat(first.runId().version()).isEqualTo(7);
		assertThat(first.candidates().getFirst().id().version()).isEqualTo(7);
		assertThat(first.candidates().getFirst().ticketCount()).isZero();
		assertThat(first.candidates().getFirst().weight()).isEqualTo(1);
		assertThat(first.candidates().getFirst().entryEvidence().entries()).hasSize(1);
		assertThat(count("draw_run_candidates")).isEqualTo(1);
		jdbc.update("update event_participants set used_ticket_count = 7 where id = ?", bytes(participant));
		jdbc.update("update event_prizes set winner_count = 5 where event_id = ?", bytes(eventId));
		when(exclusions.findExcludedParticipantIds(eventId)).thenThrow(new BusinessException(EXCLUSION_UNAVAILABLE));
		assertThat(service.prepareInitial(eventId)).isEqualTo(first);
	}

	@Test
	void readsGradesFromOriginalUseHistoryAndPreservesJson() {
		jdbc.update("update events set event_type = 'TICKET', weighting_enabled = true where id = ?", bytes(eventId));
		UUID participant = addParticipant(3);
		byte[] entry = jdbc.queryForObject("select id from event_entries where participant_id = ?", byte[].class, bytes(participant));
		for (String grade : List.of("GOLD", "SILVER", "BRONZE")) addUsedTicket(entry, grade);
		var snapshot = service.prepareInitial(eventId);
		var candidate = snapshot.candidates().getFirst();
		assertThat(candidate.ticketCount()).isEqualTo(3);
		assertThat(candidate.weight()).isEqualTo(9);
		assertThat(candidate.entryEvidence().entries().getFirst().uses()).hasSize(3);
		assertThat(candidate.entryEvidence().goldCount()).isEqualTo(1);
		assertThat(service.prepareInitial(eventId)).isEqualTo(snapshot);
	}

	@Test
	void distinguishesNormalEmptyTerminationsAndReplaysThem() {
		var empty = service.prepareInitial(eventId);
		assertThat(empty.status()).isEqualTo(DrawRunStatus.NO_ENTRIES);
		assertThat(service.prepareInitial(eventId)).isEqualTo(empty);
		assertThat(count("draw_candidates")).isZero();
	}

	@Test
	void allExcludedTerminatesWithoutResults() {
		UUID participant = addParticipant(0);
		when(exclusions.findExcludedParticipantIds(eventId)).thenReturn(Set.of(participant));
		var snapshot = service.prepareInitial(eventId);
		assertThat(snapshot.status()).isEqualTo(DrawRunStatus.NO_CANDIDATES);
		assertThat(service.prepareInitial(eventId)).isEqualTo(snapshot);
		assertThat(count("draw_candidates")).isZero();
		assertThat(count("draw_results")).isZero();
	}

	@Test
	void unavailableExclusionAndInvalidAccumulationLeaveNoRun() {
		UUID participant = addParticipant(0);
		when(exclusions.findExcludedParticipantIds(eventId)).thenThrow(new BusinessException(EXCLUSION_UNAVAILABLE));
		assertThatThrownBy(() -> service.prepareInitial(eventId)).isInstanceOf(BusinessException.class);
		assertThat(count("draw_runs")).isZero();
		jdbc.update("update event_participants set used_ticket_count = 1 where id = ?", bytes(participant));
		assertThatThrownBy(() -> service.prepareInitial(eventId)).isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(INVALID_EVIDENCE);
		assertThat(count("draw_runs")).isZero();
	}

	@Test
	void candidateLinkFailureRollsBackRunAndCandidate() {
		addParticipant(0);
		doThrow(new org.springframework.dao.DataIntegrityViolationException("test failure")).when(jdbc).update(
				eq("insert into draw_run_candidates (draw_run_id, candidate_id) values (?, ?)"), any(Object[].class));
		assertThatThrownBy(() -> service.prepareInitial(eventId))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThat(count("draw_runs")).isZero();
		assertThat(count("draw_candidates")).isZero();
		assertThat(count("draw_run_candidates")).isZero();
	}

	@Test
	void concurrentPreparationCreatesOneRunAndSnapshot() throws Exception {
		addParticipant(0);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var start = new CountDownLatch(1);
			var first = executor.submit(() -> { start.await(); return service.prepareInitial(eventId); });
			var second = executor.submit(() -> { start.await(); return service.prepareInitial(eventId); });
			start.countDown();
			assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
		}
		assertThat(count("draw_runs")).isEqualTo(1);
		assertThat(count("draw_candidates")).isEqualTo(1);
		assertThat(count("draw_run_candidates")).isEqualTo(1);
	}

	@Test
	void waitsForInFlightEntryTransactionAndIncludesItsCommit() throws Exception {
		var locked = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2)) {
			var entry = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
				jdbc.queryForObject("select id from events where id = ? for update", byte[].class, bytes(eventId));
				addParticipant(0);
				locked.countDown();
				try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
				return null;
			}));
			assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
			var started = new CountDownLatch(1);
			var drawing = executor.submit(() -> { started.countDown(); return service.prepareInitial(eventId); });
			assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> drawing.get(200, TimeUnit.MILLISECONDS))
					.isInstanceOf(java.util.concurrent.TimeoutException.class);
			release.countDown();
			entry.get(15, TimeUnit.SECONDS);
			assertThat(drawing.get(15, TimeUnit.SECONDS).candidates()).hasSize(1);
		} finally { release.countDown(); }
	}

	private UUID addParticipant(int count) {
		UUID participant = UUID.randomUUID();
		jdbc.update("insert into event_participants (id, event_id, user_id, used_ticket_count, created_at) values (?, ?, ?, ?, ?)",
				bytes(participant), bytes(eventId), bytes(userId), count, utc(NOW.minusSeconds(301)));
		jdbc.update("""
			insert into event_entries (id, participant_id, user_id, requested_ticket_count, deducted_ticket_count, created_at)
			values (?, ?, ?, ?, ?, ?)
			""", bytes(UUID.randomUUID()), bytes(participant), bytes(userId), count, count, utc(NOW.minusSeconds(301)));
		return participant;
	}

	private void addUsedTicket(byte[] entry, String grade) {
		UUID policy = UUID.randomUUID(), mission = UUID.randomUUID(), submission = UUID.randomUUID();
		UUID claim = UUID.randomUUID(), ticket = UUID.randomUUID();
		jdbc.update("""
			insert into reward_policies (id, created_by, reward_type, reward_ticket_count, effective_from, created_at)
			values (?, ?, 'MISSION', 1, ?, ?)
			""", bytes(policy), bytes(userId), utc(NOW.minusSeconds(3600)), utc(NOW.minusSeconds(3600)));
		jdbc.update("""
			insert into missions (id, reward_policy_id, created_by, title, description, mission_type,
			starts_at, ends_at, status, created_at, updated_at)
			values (?, ?, ?, '테스트', '설명', 'QUIZ', ?, ?, 'ACTIVE', ?, ?)
			""", bytes(mission), bytes(policy), bytes(userId), utc(NOW.minusSeconds(3600)), utc(NOW), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into mission_submissions (id, mission_id, reward_policy_id, user_id, is_completed, created_at)
			values (?, ?, ?, ?, true, ?)
			""", bytes(submission), bytes(mission), bytes(policy), bytes(userId), utc(NOW.minusSeconds(1000)));
		jdbc.update("""
			insert into mission_reward_claims (id, reward_policy_id, mission_id, mission_submission_id,
			user_id, source_key, ticket_count, created_at) values (?, ?, ?, ?, ?, ?, 1, ?)
			""", bytes(claim), bytes(policy), bytes(mission), bytes(submission), bytes(userId), claim.toString(), utc(NOW.minusSeconds(1000)));
		jdbc.update("""
			insert into tickets (id, user_id, mission_reward_claim_id, grade, status, expires_at, version, created_at, updated_at)
			values (?, ?, ?, ?, 'SPENT', ?, 2, ?, ?)
			""", bytes(ticket), bytes(userId), bytes(claim), grade, utc(NOW.plusSeconds(3600)), utc(NOW.minusSeconds(1000)), utc(NOW.minusSeconds(301)));
		jdbc.update("""
			insert into ticket_histories (id, ticket_id, event_entry_id, operation_type, ticket_version, status, expires_at, reason, created_at)
			values (?, ?, ?, 'USE', 2, 'SPENT', ?, '테스트 응모', ?)
			""", bytes(UUID.randomUUID()), bytes(ticket), entry, utc(NOW.plusSeconds(3600)), utc(NOW.minusSeconds(301)));
	}
	private long count(String table) {
		String sql = table.equals("draw_runs") ? "select count(*) from draw_runs where event_id = ?"
				: "select count(*) from " + table + " where draw_run_id in (select id from draw_runs where event_id = ?)";
		return jdbc.queryForObject(sql, Long.class, bytes(eventId));
	}
	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}
	private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@ComponentScan(basePackages = {"com.getddo.core.drawing", "com.getddo.db.drawing"})
	@Import(MySqlTestConfiguration.class)
	static class TestApplication {
		@Bean MutableClock clock() { return new MutableClock(NOW); }
		@Bean TimeProvider timeProvider(Clock clock) { return new TimeProvider(clock); }
	}
}
