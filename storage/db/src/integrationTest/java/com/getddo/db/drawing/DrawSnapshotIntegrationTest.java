package com.getddo.db.drawing;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.service.DrawSnapshotService;
import com.getddo.db.common.config.JpaAuditingConfig;
import com.getddo.db.drawing.entity.DrawCandidateEntity;
import com.getddo.db.drawing.entity.DrawRunCandidateEntity;
import com.getddo.db.drawing.entity.DrawRunCandidateId;
import com.getddo.db.drawing.entity.DrawRunEntity;
import com.getddo.db.drawing.repository.DrawCandidateJpaRepository;
import com.getddo.db.drawing.repository.DrawRunJpaRepository;
import com.getddo.db.drawing.repository.DrawRunCandidateJpaRepository;
import com.getddo.db.support.MySqlTestConfiguration;
import com.getddo.db.ticket.MutableClock;
import com.getddo.db.ticket.LockWaitProbe;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = DrawSnapshotIntegrationTest.TestApplication.class,
		properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"})
class DrawSnapshotIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-09T03:00:00Z");
	@Autowired private DrawSnapshotService service;
	@Autowired private MutableClock clock;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MySQLContainer mysql;
	@Autowired private DrawRunJpaRepository runs;
	@Autowired private DrawCandidateJpaRepository candidates;
	@MockitoSpyBean private DrawRunCandidateJpaRepository links;
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
	}

	@Test
	void storesAndReplaysEvidenceWithoutRecomputingOriginals() {
		UUID participant = addParticipant(0);
		DrawSnapshot first = service.prepareInitial(eventId);
		assertThat(first.status()).isEqualTo(DrawRunStatus.READY);
		assertThat(first.runId().version()).isEqualTo(7);
		assertThat(first.candidates().getFirst().id().version()).isEqualTo(7);
		assertThat(first.candidates().getFirst().ticketCount()).isZero();
		assertThat(first.candidates().getFirst().weight()).isEqualTo(1);
		assertThat(first.candidates().getFirst().entryEvidence().entries()).hasSize(1);
		assertThat(count("draw_run_candidates")).isEqualTo(1);
		jdbc.update("update event_participants set used_ticket_count = 7 where id = ?", bytes(participant));
		jdbc.update("update event_prizes set winner_count = 5 where event_id = ?", bytes(eventId));
		assertThat(service.prepareInitial(eventId)).isEqualTo(first);
	}

	@Test
	void readsGradesFromOriginalUseHistoryAndPreservesJson() {
		jdbc.update("update events set event_type = 'TICKET', weighting_enabled = true where id = ?", bytes(eventId));
		UUID participant = addParticipant(3);
		byte[] entry = jdbc.queryForObject("select id from event_entries where participant_id = ?", byte[].class, bytes(participant));
		for (String grade : List.of("GOLD", "SILVER", "BRONZE")) addUsedTicket(entry, grade);
		DrawSnapshot snapshot = service.prepareInitial(eventId);
		DrawSnapshot.Candidate candidate = snapshot.candidates().getFirst();
		assertThat(candidate.ticketCount()).isEqualTo(3);
		assertThat(candidate.weight()).isEqualTo(9);
		assertThat(candidate.entryEvidence().entries().getFirst().uses()).hasSize(3);
		assertThat(candidate.entryEvidence().goldCount()).isEqualTo(1);
		assertThat(service.prepareInitial(eventId)).isEqualTo(snapshot);
	}

	@Test
	void mapsBinaryIdsJsonAndUtcTimesThroughJpa() {
		addParticipant(0);
		DrawSnapshot snapshot = service.prepareInitial(eventId);
		DrawSnapshot.Candidate candidate = snapshot.candidates().getFirst();
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			DrawRunEntity run = runs.findById(snapshot.runId()).orElseThrow();
			assertThat(run.getEventId()).isEqualTo(eventId);
			assertThat(run.getCreatedAt()).isEqualTo(NOW);
			assertThat(run.getSnapshotFixedAt()).isEqualTo(NOW);
			assertThat(run.getStartedAt()).isNull();
			assertThat(run.getConfirmedAt()).isNull();
			DrawRunCandidateEntity link = links.findById(new DrawRunCandidateId(snapshot.runId(), candidate.id())).orElseThrow();
			assertThat(link.isNew()).isFalse();
			assertThat(link.getDrawRun().getId()).isEqualTo(snapshot.runId());
			assertThat(link.getCandidate().getParticipantId()).isEqualTo(candidate.participantId());
			assertThat(link.getCandidate().getCreatedAt()).isEqualTo(NOW);
		});
		assertThat(jdbc.queryForObject("select json_type(rules_snapshot) from draw_runs where id = ?",
				String.class, bytes(snapshot.runId()))).isEqualTo("OBJECT");
		assertThat(jdbc.queryForObject("select json_type(entry_snapshot) from draw_candidates where id = ?",
				String.class, bytes(candidate.id()))).isEqualTo("OBJECT");
		assertThat(jdbc.queryForObject("select json_type(eligibility_snapshot) from draw_candidates where id = ?",
				String.class, bytes(candidate.id()))).isEqualTo("OBJECT");
		assertThat(service.prepareInitial(eventId)).isEqualTo(snapshot);
	}

	@Test
	@DisplayName("추가 응모 간 티켓 중복이 DB에 기록됐어도 스냅샷을 저장하지 않는다")
	void rejectsDuplicateTicketAcrossPersistedAdditionalEntries() {
		// given
		jdbc.update("update events set event_type = 'TICKET', weighting_enabled = true where id = ?", bytes(eventId));
		UUID participant = addParticipant(2);
		byte[] firstEntry = jdbc.queryForObject("select id from event_entries where participant_id = ?", byte[].class, bytes(participant));
		jdbc.update("update event_entries set requested_ticket_count = 1, deducted_ticket_count = 1 where id = ?", firstEntry);
		addUsedTicket(firstEntry, "GOLD");
		byte[] ticket = jdbc.queryForObject("select ticket_id from ticket_histories where event_entry_id = ?",
				byte[].class, firstEntry);
		UUID additionalEntry = UUID.randomUUID();
		jdbc.update("""
			insert into event_entries (id, participant_id, user_id, requested_ticket_count, deducted_ticket_count, created_at)
			values (?, ?, ?, 1, 1, ?)
			""", bytes(additionalEntry), bytes(participant), bytes(userId), utc(NOW.minusSeconds(301)));
		// 응모별 UNIQUE로 거절할 수 없는 별도 이력·버전의 중복 사용을 재현한다.
		jdbc.update("""
			insert into ticket_histories (id, ticket_id, event_entry_id, operation_type, ticket_version, status, expires_at, reason, created_at)
			values (?, ?, ?, 'USE', 3, 'SPENT', ?, '중복 사용 검증', ?)
			""", bytes(UUID.randomUUID()), ticket, bytes(additionalEntry), utc(NOW.plusSeconds(3600)), utc(NOW.minusSeconds(301)));

		// when / then
		assertThatThrownBy(() -> service.prepareInitial(eventId)).isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(INVALID_EVIDENCE);
		assertThat(count("draw_runs")).isZero();
		assertThat(count("draw_candidates")).isZero();
		assertThat(count("draw_run_candidates")).isZero();
	}

	@Test
	void firstResponseUsesPersistedMicrosecondPrecision() {
		clock.set(NOW.plusNanos(123456789));
		addParticipant(0);
		DrawSnapshot first = service.prepareInitial(eventId);
		assertThat(first.fixedAt().getNano() % 1000).isZero();
		assertThat(service.prepareInitial(eventId)).isEqualTo(first);
	}

	@Test
	void jpaWritesRespectRunCandidateAndLinkUniqueness() {
		addParticipant(0);
		DrawSnapshot snapshot = service.prepareInitial(eventId);
		UUID candidateId = snapshot.candidates().getFirst().id();
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
				runs.saveAndFlush(new DrawRunEntity(eventId))))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			DrawCandidateEntity original = candidates.findById(candidateId).orElseThrow();
			candidates.saveAndFlush(new DrawCandidateEntity(original.getDrawRun(), original.getParticipantId(),
					original.getTicketCount(), original.getWeight(), original.getEntrySnapshot(), original.getEligibilitySnapshot()));
		})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			DrawRunEntity run = runs.findById(snapshot.runId()).orElseThrow();
			DrawCandidateEntity candidate = candidates.findById(candidateId).orElseThrow();
			links.saveAndFlush(new DrawRunCandidateEntity(run, candidate));
		})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThat(count("draw_runs")).isEqualTo(1);
		assertThat(count("draw_candidates")).isEqualTo(1);
		assertThat(count("draw_run_candidates")).isEqualTo(1);
		assertThat(service.prepareInitial(eventId)).isEqualTo(snapshot);
	}

	@Test
	void jpaWritesRespectParticipantForeignKeyAndPositiveWeight() {
		addParticipant(0);
		DrawSnapshot snapshot = service.prepareInitial(eventId);
		UUID candidateId = snapshot.candidates().getFirst().id();
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			DrawCandidateEntity original = candidates.findById(candidateId).orElseThrow();
			candidates.saveAndFlush(new DrawCandidateEntity(original.getDrawRun(), UUID.randomUUID(), 0, 1,
					original.getEntrySnapshot(), original.getEligibilitySnapshot()));
		})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
				.hasMessageContaining("fk_draw_candidates_2");
		UUID otherUser = UUID.randomUUID(), otherParticipant = UUID.randomUUID();
		jdbc.update("""
			insert into users (id, name, role, status, membership, created_at, updated_at)
			values (?, '추첨 테스트', 'USER', 'ACTIVE', 'VIP', ?, ?)
			""", bytes(otherUser), utc(NOW), utc(NOW));
		jdbc.update("insert into event_participants (id, event_id, user_id, used_ticket_count, created_at) values (?, ?, ?, 0, ?)",
				bytes(otherParticipant), bytes(eventId), bytes(otherUser), utc(NOW));
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			DrawCandidateEntity original = candidates.findById(candidateId).orElseThrow();
			candidates.saveAndFlush(new DrawCandidateEntity(original.getDrawRun(), otherParticipant, 0, 0,
					original.getEntrySnapshot(), original.getEligibilitySnapshot()));
		})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
				.hasMessageContaining("chk_candidate_counts");
		assertThat(count("draw_candidates")).isEqualTo(1);
	}

	@Test
	void distinguishesNormalEmptyTerminationsAndReplaysThem() {
		DrawSnapshot empty = service.prepareInitial(eventId);
		assertThat(empty.status()).isEqualTo(DrawRunStatus.NO_ENTRIES);
		assertThat(service.prepareInitial(eventId)).isEqualTo(empty);
		assertThat(count("draw_candidates")).isZero();
	}

	/** 기존 어뷰징 판단 기록이 있어도 다른 정상 응모를 후보에서 제거하지 않는다. */
	@Test
	@DisplayName("과거 어뷰징 판단 기록이 있어도 정상 응모를 후보로 보존한다")
	void preparesValidParticipantDespiteLegacyAbuseRecord() {
		// given
		UUID participant = addParticipant(0);
		UUID abuseCaseId = UUID.randomUUID();
		jdbc.update("""
			insert into abuse_cases (id, event_id, user_id, reviewed_by, request_id, detection_key,
				detection_type, detection_reason, evidence, occurred_at, detected_at, review_status, reviewed_at, review_reason)
			values (?, ?, ?, ?, ?, ?, 'LEGACY_TEST', '과거 판단 기록', '{}', ?, ?, 'CONFIRMED', ?, '과거 판단')
			""", bytes(abuseCaseId), bytes(eventId), bytes(userId), bytes(userId), abuseCaseId.toString(),
				abuseCaseId.toString(), utc(NOW.minusSeconds(1000)), utc(NOW.minusSeconds(900)), utc(NOW.minusSeconds(800)));

		// when
		DrawSnapshot snapshot = service.prepareInitial(eventId);

		// then
		assertThat(snapshot.status()).isEqualTo(DrawRunStatus.READY);
		assertThat(snapshot.candidates()).extracting(DrawSnapshot.Candidate::participantId).containsExactly(participant);
		assertThat(snapshot.candidates().getFirst().eligibilityEvidence().excluded()).isFalse();
		assertThat(count("draw_candidates")).isEqualTo(1);
		assertThat(count("draw_run_candidates")).isEqualTo(1);
		assertThat(service.prepareInitial(eventId)).isEqualTo(snapshot);
	}

	/** 신규 전원 제외 종료는 만들지 않지만 과거 DB의 NO_CANDIDATES는 재계산 없이 조회한다. */
	@Test
	@DisplayName("과거 NO_CANDIDATES 실행은 새 응모가 있어도 재계산하지 않는다")
	void replaysLegacyNoCandidatesWithoutRecalculatingNewEntries() {
		// given
		DrawSnapshot empty = service.prepareInitial(eventId);
		jdbc.update("update draw_runs set status = 'NO_CANDIDATES' where id = ?", bytes(empty.runId()));
		addParticipant(0);

		// when
		DrawSnapshot historical = service.prepareInitial(eventId);

		// then
		assertThat(historical.runId()).isEqualTo(empty.runId());
		assertThat(historical.status()).isEqualTo(DrawRunStatus.NO_CANDIDATES);
		assertThat(historical.fixedAt()).isEqualTo(empty.fixedAt());
		assertThat(historical.rules()).isEqualTo(empty.rules());
		assertThat(historical.candidates()).isEmpty();
		assertThat(service.prepareInitial(eventId)).isEqualTo(historical);
		assertThat(count("draw_candidates")).isZero();
		assertThat(count("draw_results")).isZero();
	}

	/** 제외 기능 유무와 무관하게 누적 사용 장수 불일치는 전체 준비를 거절한다. */
	@Test
	@DisplayName("누적 사용 장수가 다르면 준비를 거절하고 실행·후보·연결을 저장하지 않는다")
	void invalidAccumulationLeavesNoRun() {
		// given
		UUID participant = addParticipant(0);
		jdbc.update("update event_participants set used_ticket_count = 1 where id = ?", bytes(participant));

		// when
		Throwable failure = catchThrowable(() -> service.prepareInitial(eventId));

		// then
		assertThat(failure).isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(INVALID_EVIDENCE);
		assertThat(count("draw_runs")).isZero();
		assertThat(count("draw_candidates")).isZero();
		assertThat(count("draw_run_candidates")).isZero();
	}

	@Test
	void candidateLinkFailureRollsBackRunAndCandidate() {
		addParticipant(0);
		doThrow(new org.springframework.dao.DataIntegrityViolationException("test failure"))
				.when(links).saveAllAndFlush(anyList());
		assertThatThrownBy(() -> service.prepareInitial(eventId))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThat(count("draw_runs")).isZero();
		assertThat(count("draw_candidates")).isZero();
		assertThat(count("draw_run_candidates")).isZero();
	}

	@Test
	void concurrentPreparationCreatesOneRunAndSnapshot() throws Exception {
		addParticipant(0);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			CountDownLatch start = new CountDownLatch(1);
			Future<DrawSnapshot> first = executor.submit(() -> { start.await(); return service.prepareInitial(eventId); });
			Future<DrawSnapshot> second = executor.submit(() -> { start.await(); return service.prepareInitial(eventId); });
			start.countDown();
			assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
		}
		assertThat(count("draw_runs")).isEqualTo(1);
		assertThat(count("draw_candidates")).isEqualTo(1);
		assertThat(count("draw_run_candidates")).isEqualTo(1);
	}

	@Test
	@DisplayName("진행 중인 응모 SQL 트랜잭션의 실제 이벤트 잠금 대기를 확인하고 커밋된 후보를 포함한다")
	void waitsForInFlightEntryTransactionAndIncludesItsCommit() throws Exception {
		// given
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			// 실제 응모 서비스가 추가되기 전까지 합의된 이벤트 선잠금 계약을 SQL로 검증한다.
			Future<UUID> entry = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
				jdbc.queryForObject("select id from events where id = ? for update", byte[].class, bytes(eventId));
				UUID participant = addParticipant(0);
				locked.countDown();
				try { if (!release.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
				return participant;
			}));
			try {
				assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
				// when
				Future<DrawSnapshot> drawing = executor.submit(() -> service.prepareInitial(eventId));
				new LockWaitProbe(mysql).awaitLockWaits(1);
				assertThat(drawing.isDone()).isFalse();
				release.countDown();
				UUID committedParticipant = entry.get(15, TimeUnit.SECONDS);
				DrawSnapshot snapshot = drawing.get(15, TimeUnit.SECONDS);
				// then
				assertThat(snapshot.candidates()).hasSize(1);
				assertThat(snapshot.candidates().getFirst().participantId()).isEqualTo(committedParticipant);
				assertThat(count("draw_runs")).isEqualTo(1);
				assertThat(count("draw_candidates")).isEqualTo(1);
				assertThat(count("draw_run_candidates")).isEqualTo(1);
			} finally { release.countDown(); }
		}
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
	@Import({MySqlTestConfiguration.class, JpaAuditingConfig.class})
	static class TestApplication {
		@Bean MutableClock clock() { return new MutableClock(NOW); }
		@Bean TimeProvider timeProvider(Clock clock) { return new TimeProvider(clock); }
	}
}
