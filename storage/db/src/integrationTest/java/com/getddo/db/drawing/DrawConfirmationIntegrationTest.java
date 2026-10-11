package com.getddo.db.drawing;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.DrawExclusionRepository;
import com.getddo.core.drawing.service.DrawConfirmationService;
import com.getddo.core.drawing.service.DrawSnapshotService;
import com.getddo.core.drawing.service.DrawingSelectionService;
import com.getddo.db.drawing.entity.DrawResultEntity;
import com.getddo.db.drawing.entity.DrawRunCandidateId;
import com.getddo.db.drawing.repository.*;
import com.getddo.db.ticket.LockWaitProbe;
import com.getddo.db.ticket.MutableClock;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;
import static com.getddo.db.common.util.UuidBinary.toBytes;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = DrawSnapshotIntegrationTest.TestApplication.class,
		properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"})
class DrawConfirmationIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
	@Autowired private DrawConfirmationService service;
	@Autowired private DrawSnapshotService snapshots;
	@Autowired private DrawCandidateJpaRepository candidates;
	@Autowired private DrawRunCandidateJpaRepository links;
	@Autowired private DrawResultRepositoryImpl repository;
	@Autowired private MutableClock clock;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager entityManager;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private MySQLContainer mysql;
	@MockitoBean private DrawExclusionRepository exclusions;
	@MockitoSpyBean private DrawResultJpaRepository results;
	@MockitoSpyBean private DrawRunJpaRepository runs;
	@MockitoSpyBean private DrawingSelectionService selection;
	private UUID eventId, prizeId, userId;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		userId = addUser();
		eventId = addEvent();
		prizeId = addPrize(eventId, 1, 2);
		when(exclusions.findExcludedParticipantIds(any())).thenReturn(Set.of());
	}

	@Test
	@DisplayName("선정·미충원 JPA 매핑과 재조회에서 ID·선정 순서·저장 시각을 보존한다")
	void persistsAndReplaysResultsWithoutPublishingOrReadingOriginals() {
		// given
		addParticipant(eventId, userId);
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		clock.set(NOW.plusNanos(123456789));
		// when
		DrawConfirmation first = service.confirm(snapshot.runId());
		// then
		assertThat(first.getResults()).hasSize(2);
		assertThat(first.getResults().getFirst().getSelection().getSelectionOrder()).isEqualTo(1);
		assertThat(first.getResults().getLast().getSelection().getResultType()).isEqualTo(DrawSelectionResultType.UNFILLED);
		assertThat(first.getConfirmedAt().getNano() % 1000).isZero();
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			var run = runs.findById(snapshot.runId()).orElseThrow();
			assertThat(run.getStatus()).isEqualTo(DrawRunStatus.CONFIRMED);
			assertThat(run.getConfirmedAt()).isEqualTo(first.getConfirmedAt());
			assertThat(run.getStartedAt()).isEqualTo(first.getConfirmedAt());
			var stored = results.findById(first.getResults().getFirst().getId()).orElseThrow();
			assertThat(stored.getId().version()).isEqualTo(7);
			assertThat(stored.getCreatedAt().getNano() % 1000).isZero();
			assertThat(stored.getDrawRun().getId()).isEqualTo(snapshot.runId());
			assertThat(stored.getRunCandidate().getId()).isEqualTo(new DrawRunCandidateId(snapshot.runId(), snapshot.candidates().getFirst().id()));
			var unfilled = results.findById(first.getResults().getLast().getId()).orElseThrow();
			assertThat(unfilled.getCandidateId()).isNull();
			assertThat(unfilled.getRunCandidate()).isNull();
			assertThat(unfilled.getSelectionOrder()).isNull();
		});
		// 현재 원본 변화·취소 상태는 확정된 동일 실행의 결과 재요청을 바꾸지 않는다.
		jdbc.update("update event_prizes set winner_count = 5, prize_rank = 3 where id = ?", toBytes(prizeId));
		jdbc.update("update events set status = 'CANCELED' where id = ?", toBytes(eventId));
		jdbc.update("update event_participants set used_ticket_count = 99 where event_id = ?", toBytes(eventId));
		when(exclusions.findExcludedParticipantIds(any())).thenThrow(new BusinessException(EXCLUSION_UNAVAILABLE));
		DrawConfirmation replay = service.confirm(snapshot.runId());
		assertThat(replay).usingRecursiveComparison().isEqualTo(first);
		verify(selection, times(1)).select(anyList(), anyList(), anyBoolean());
		assertThat(count("draw_publications")).isZero();
	}

	@Test
	@DisplayName("상위 경품부터 같은 사용자를 한 번만 선정하고 후보 소진 후 미충원으로 저장한다")
	void fillsHigherRanksWithoutDuplicateUsers() {
		// given
		addPrize(eventId, 2, 2);
		for (int index = 0; index < 3; index++) addParticipant(eventId, addUser());
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		// when
		var selections = service.confirm(snapshot.runId()).getResults().stream().map(DrawResult::getSelection).toList();
		// then
		assertThat(selections).extracting(DrawSelection::getRank).containsExactly(1, 1, 2, 2);
		assertThat(selections).filteredOn(s -> s.getResultType() == DrawSelectionResultType.SELECTED)
				.extracting(DrawSelection::getUserId).doesNotHaveDuplicates().hasSize(3);
		assertThat(selections).extracting(DrawSelection::getSelectionOrder).containsExactly(1, 2, 3, null);
	}

	@Test
	@DisplayName("결과 저장 중 실패한 변경을 롤백하고 같은 스냅샷으로 재시도한다")
	void rollsBackFlushedResultsAndRetriesSnapshot() {
		// given
		addParticipant(eventId, userId);
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		doAnswer(invocation -> {
			List<DrawResultEntity> pending = invocation.getArgument(0);
			pending.forEach(entityManager::persist);
			entityManager.flush();
			throw new IllegalStateException("저장 이후 실패");
		})
				.when(results).saveAllAndFlush(anyList());
		// when / then
		assertThatThrownBy(() -> service.confirm(snapshot.runId()))
				.isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertReadyWithoutResults(snapshot);
		reset(results);
		assertThat(service.confirm(snapshot.runId()).getResults()).hasSize(2);
		assertThat(snapshots.prepareInitial(eventId).candidates()).isEqualTo(snapshot.candidates());
		verify(selection, times(2)).select(anyList(), anyList(), anyBoolean());
	}

	@Test
	@DisplayName("CONFIRMED 상태 flush 이후 실패해도 결과·상태·시각을 모두 롤백한다")
	void rollsBackStateTransitionAndResults() {
		// given
		addParticipant(eventId, userId);
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		doAnswer(invocation -> { entityManager.flush(); throw new IllegalStateException("확정 이후 실패"); })
				.when(runs).flush();
		// when / then
		assertThatThrownBy(() -> service.confirm(snapshot.runId()))
				.isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertReadyWithoutResults(snapshot);
		assertThat(runs.findById(snapshot.runId()).orElseThrow().getConfirmedAt()).isNull();
		assertThat(runs.findById(snapshot.runId()).orElseThrow().getStartedAt()).isNull();
		reset(runs);
		assertThat(service.confirm(snapshot.runId()).getResults()).hasSize(2);
	}

	@Test
	@DisplayName("동시 확정 요청이 실제 이벤트 잠금에서 대기하고 같은 저장 결과를 받는다")
	void concurrentConfirmationSelectsOnce() throws Exception {
		// given
		addParticipant(eventId, userId);
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		CountDownLatch selecting = new CountDownLatch(1), release = new CountDownLatch(1);
		doAnswer(invocation -> {
			selecting.countDown();
			if (!release.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
			return invocation.callRealMethod();
		}).when(selection).select(anyList(), anyList(), anyBoolean());
		try (var executor = Executors.newFixedThreadPool(2)) {
			var first = executor.submit(() -> service.confirm(snapshot.runId()));
			try {
				assertThat(selecting.await(10, TimeUnit.SECONDS)).isTrue();
				// when
				var second = executor.submit(() -> service.confirm(snapshot.runId()));
				new LockWaitProbe(mysql).awaitLockWaits(1);
				assertThat(second.isDone()).isFalse();
				release.countDown();
				// then
				assertThat(first.get(15, TimeUnit.SECONDS)).usingRecursiveComparison().isEqualTo(second.get(15, TimeUnit.SECONDS));
			} finally { release.countDown(); }
		}
		assertThat(count("draw_results")).isEqualTo(2);
		verify(selection, times(1)).select(anyList(), anyList(), anyBoolean());
	}

	@Test
	@DisplayName("DB에서 허용되는 타 이벤트 후보 연결을 확정 전에 거절한다")
	void rejectsCandidateFromAnotherEvent() {
		// given
		addParticipant(eventId, userId);
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		UUID foreignEvent = addEvent();
		addPrize(foreignEvent, 1, 1);
		addParticipant(foreignEvent, addUser());
		var foreign = snapshots.prepareInitial(foreignEvent);
		jdbc.update("insert into draw_run_candidates (draw_run_id, candidate_id) values (?, ?)",
				toBytes(snapshot.runId()), toBytes(foreign.candidates().getFirst().id()));
		// when / then
		assertInvalid(snapshot);
		assertReadyWithoutResults(snapshot);
	}

	@Test
	@DisplayName("후보 스냅샷 사용자와 실제 참가자 사용자가 다르면 확정하지 않는다")
	void rejectsCandidateOwnerMismatch() {
		// given
		addParticipant(eventId, userId);
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		UUID unrelatedUser = addUser();
		jdbc.update("update draw_candidates set eligibility_snapshot = json_set(eligibility_snapshot, '$.userId', ?) where id = ?",
				unrelatedUser.toString(), toBytes(snapshot.candidates().getFirst().id()));
		// when / then
		assertInvalid(snapshot);
		assertReadyWithoutResults(snapshot);
	}

	@Test
	@DisplayName("규칙 스냅샷에 다른 이벤트의 경품이 있으면 확정하지 않는다")
	void rejectsPrizeFromAnotherEvent() {
		// given
		addParticipant(eventId, userId);
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		UUID foreignPrize = addPrize(addEvent(), 1, 1);
		jdbc.update("update draw_runs set rules_snapshot = json_set(rules_snapshot, '$.prizes[0].id', ?) where id = ?",
				foreignPrize.toString(), toBytes(snapshot.runId()));
		// when / then
		assertInvalid(snapshot);
		assertReadyWithoutResults(snapshot);
	}

	@Test
	@DisplayName("기존 유효 배정 충돌은 거절하고 취소 이력에 연결된 배정은 현재 당첨에서 제외한다")
	void detectsCurrentAwardAndExcludesCancellation() {
		// given
		addParticipant(eventId, userId);
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		UUID priorRun = UUID.randomUUID(), priorResult = UUID.randomUUID();
		jdbc.update("""
			insert into draw_runs (id, event_id, run_number, draw_type, status, algorithm_version,
			rules_snapshot, snapshot_fixed_at, started_at, confirmed_at, created_at)
			select ?, event_id, 1, 'REPLACEMENT', 'CONFIRMED', algorithm_version, rules_snapshot,
			snapshot_fixed_at, ?, ?, ? from draw_runs where id = ?
			""", toBytes(priorRun), utc(NOW), utc(NOW), utc(NOW), toBytes(snapshot.runId()));
		UUID candidate = snapshot.candidates().getFirst().id();
		jdbc.update("insert into draw_run_candidates (draw_run_id, candidate_id) values (?, ?)", toBytes(priorRun), toBytes(candidate));
		jdbc.update("""
			insert into draw_results (id, draw_run_id, event_prize_id, slot_number, result_type, candidate_id, selection_order, created_at)
			values (?, ?, ?, 1, 'SELECTED', ?, 1, ?)
			""", toBytes(priorResult), toBytes(priorRun), toBytes(prizeId), toBytes(candidate), utc(NOW));
		// when / then
		assertInvalid(snapshot);
		assertThat(repository.findCurrentAwards(eventId)).hasSize(1);
		jdbc.update("""
			insert into award_cancellations (id, draw_result_id, canceled_by, reason, canceled_at)
			values (?, ?, ?, '테스트 취소', ?)
			""", toBytes(UUID.randomUUID()), toBytes(priorResult), toBytes(userId), utc(NOW));
		assertThat(repository.findCurrentAwards(eventId)).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from draw_results where id = ?", Long.class, toBytes(priorResult))).isEqualTo(1);
		assertThat(service.confirm(snapshot.runId()).getResults()).hasSize(2);
	}

	@Test
	@DisplayName("결과 JPA 저장은 실행별 자리·후보·순서 UNIQUE와 후보 연결 복합 FK를 준수한다")
	void enforcesDatabaseConstraintsForJpaWrites() {
		// given
		addParticipant(eventId, userId);
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		DrawConfirmation confirmed = service.confirm(snapshot.runId());
		DrawSelection selected = confirmed.getResults().getFirst().getSelection();
		// when / then
		assertJpaConstraint(snapshot, selected, "uq_draw_results_1");
		assertJpaConstraint(snapshot, new DrawSelection(prizeId, 1, 3, 2, selected.getCandidateId(), userId,
				DrawSelectionResultType.SELECTED), "uq_draw_results_2");
		UUID another = addParticipant(eventId, addUser());
		UUID candidateId = UUID.randomUUID();
		var evidence = snapshot.candidates().getFirst();
		jdbc.update("""
			insert into draw_candidates (id, participant_id, draw_run_id, ticket_count, weight, entry_snapshot, eligibility_snapshot, created_at)
			select ?, ?, draw_run_id, ticket_count, weight, entry_snapshot, eligibility_snapshot, created_at
			from draw_candidates where id = ?
			""", toBytes(candidateId), toBytes(another), toBytes(evidence.id()));
		DrawSelection unlinked = new DrawSelection(prizeId, 1, 3, 2, candidateId, UUID.randomUUID(), DrawSelectionResultType.SELECTED);
		assertJpaConstraint(snapshot, unlinked, "fk_draw_results_3");
		jdbc.update("insert into draw_run_candidates (draw_run_id, candidate_id) values (?, ?)", toBytes(snapshot.runId()), toBytes(candidateId));
		assertJpaConstraint(snapshot, new DrawSelection(prizeId, 1, 3, 1, candidateId, UUID.randomUUID(),
				DrawSelectionResultType.SELECTED), "uq_draw_results_3");
	}

	private void assertJpaConstraint(DrawSnapshot snapshot, DrawSelection selection, String constraint) {
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			var run = runs.findById(snapshot.runId()).orElseThrow();
			results.saveAndFlush(new DrawResultEntity(run, selection));
		})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining(constraint);
	}
	private void assertInvalid(DrawSnapshot snapshot) {
		assertThatThrownBy(() -> service.confirm(snapshot.runId())).isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(INVALID_RUN);
	}
	private void assertReadyWithoutResults(DrawSnapshot snapshot) {
		assertThat(runs.findById(snapshot.runId()).orElseThrow().getStatus()).isEqualTo(DrawRunStatus.READY);
		assertThat(results.findByDrawRunId(snapshot.runId())).isEmpty();
	}
	private UUID addUser() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
			insert into users (id, name, role, status, membership, created_at, updated_at)
			values (?, '확정 테스트', 'USER', 'ACTIVE', 'VIP', ?, ?)
			""", toBytes(id), utc(NOW), utc(NOW));
		return id;
	}
	private UUID addEvent() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
			insert into events (id, title, description, image_key, event_type, weighting_enabled,
			starts_at, ends_at, status, membership_rule, created_at, updated_at)
			values (?, '확정 테스트', '설명', 'test.png', 'NO_TICKET', false, ?, ?, 'CLOSED', 'vip', ?, ?)
			""", toBytes(id), utc(NOW.minusSeconds(3600)), utc(NOW.minusSeconds(300)), utc(NOW), utc(NOW));
		return id;
	}
	private UUID addPrize(UUID event, int rank, int count) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
			insert into event_prizes (id, event_id, prize_rank, name, winner_count, created_at, updated_at)
			values (?, ?, ?, '경품', ?, ?, ?)
			""", toBytes(id), toBytes(event), rank, count, utc(NOW), utc(NOW));
		return id;
	}
	private UUID addParticipant(UUID event, UUID user) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into event_participants (id, event_id, user_id, used_ticket_count, created_at) values (?, ?, ?, 0, ?)",
				toBytes(id), toBytes(event), toBytes(user), utc(NOW.minusSeconds(301)));
		jdbc.update("""
			insert into event_entries (id, participant_id, user_id, requested_ticket_count, deducted_ticket_count, created_at)
			values (?, ?, ?, 0, 0, ?)
			""", toBytes(UUID.randomUUID()), toBytes(id), toBytes(user), utc(NOW.minusSeconds(301)));
		return id;
	}
	private long count(String table) {
		String sql = table.equals("draw_results")
				? "select count(*) from draw_results where draw_run_id in (select id from draw_runs where event_id = ?)"
				: "select count(*) from draw_publications where event_id = ?";
		return jdbc.queryForObject(sql, Long.class, toBytes(eventId));
	}
	private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
}
