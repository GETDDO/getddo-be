package com.getddo.db.drawing;

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

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.service.DrawConfirmationService;
import com.getddo.core.drawing.service.DrawSnapshotService;
import com.getddo.core.drawing.service.DrawingSelectionService;
import com.getddo.db.drawing.entity.DrawResultEntity;
import com.getddo.db.drawing.entity.DrawRunEntity;
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
	@MockitoSpyBean private DrawResultJpaRepository results;
	@MockitoSpyBean private DrawRunJpaRepository runs;
	@MockitoSpyBean private DrawingSelectionService selection;
	private UUID eventId, prizeId, userId;

	/** 테스트마다 별도 이벤트·사용자를 만들고 확정 시각을 고정한다. */
	@BeforeEach
	void setUp() {
		clock.set(NOW);
		userId = addUser();
		eventId = addEvent();
		prizeId = addPrize(eventId, 1, 2);
	}

	/** JPA 왕복과 실제 시각 정밀도·복합 연결을 검증하고 원본 변경 후에도 확정 재요청이 동일한지 확인한다. */
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
			DrawRunEntity run = runs.findById(snapshot.runId()).orElseThrow();
			assertThat(run.getStatus()).isEqualTo(DrawRunStatus.CONFIRMED);
			assertThat(run.getConfirmedAt()).isEqualTo(first.getConfirmedAt());
			assertThat(run.getStartedAt()).isEqualTo(first.getConfirmedAt());
			DrawResultEntity stored = results.findById(first.getResults().getFirst().getId()).orElseThrow();
			assertThat(stored.getId().version()).isEqualTo(7);
			assertThat(stored.getCreatedAt().getNano() % 1000).isZero();
			assertThat(stored.getDrawRun().getId()).isEqualTo(snapshot.runId());
			assertThat(stored.getRunCandidate().getId()).isEqualTo(new DrawRunCandidateId(snapshot.runId(), snapshot.candidates().getFirst().id()));
			DrawResultEntity unfilled = results.findById(first.getResults().getLast().getId()).orElseThrow();
			assertThat(unfilled.getCandidateId()).isNull();
			assertThat(unfilled.getRunCandidate()).isNull();
			assertThat(unfilled.getSelectionOrder()).isNull();
		});
		// 현재 원본 변화·취소 상태는 확정된 동일 실행의 결과 재요청을 바꾸지 않는다.
		jdbc.update("update event_prizes set winner_count = 5, prize_rank = 3 where id = ?", toBytes(prizeId));
		jdbc.update("update events set status = 'CANCELED' where id = ?", toBytes(eventId));
		jdbc.update("update event_participants set used_ticket_count = 99 where event_id = ?", toBytes(eventId));
		DrawConfirmation replay = service.confirm(snapshot.runId());
		assertThat(replay).usingRecursiveComparison().isEqualTo(first);
		verify(selection, times(1)).select(anyList(), anyList(), anyBoolean());
		assertThat(count("draw_publications")).isZero();
	}

	/** 실제 DB 저장 결과에서 상위 등수 우선·사용자 중복 방지·후보 소진 이후 미충원을 검증한다. */
	@Test
	@DisplayName("상위 경품부터 같은 사용자를 한 번만 선정하고 후보 소진 후 미충원으로 저장한다")
	void fillsHigherRanksWithoutDuplicateUsers() {
		// given
		addPrize(eventId, 2, 2);
		for (int index = 0; index < 3; index++) addParticipant(eventId, addUser());
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		// when
		List<DrawSelection> selections = service.confirm(snapshot.runId()).getResults().stream().map(DrawResult::getSelection).toList();
		// then
		assertThat(selections).extracting(DrawSelection::getRank).containsExactly(1, 1, 2, 2);
		assertThat(selections).filteredOn(s -> s.getResultType() == DrawSelectionResultType.SELECTED)
				.extracting(DrawSelection::getUserId).doesNotHaveDuplicates().hasSize(3);
		assertThat(selections).extracting(DrawSelection::getSelectionOrder).containsExactly(1, 2, 3, null);
	}

	/** 결과 flush 직후 실패를 주입하여 서비스 트랜잭션의 전체 롤백과 저장 스냅샷 재시도를 검증한다. */
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

	/** 상태 변경 flush 이후 실패해도 결과·상태·시각이 모두 롤백되는지 검증한다. */
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

	/** 두 서비스 호출을 독립 트랜잭션으로 경합시켜 실제 이벤트 잠금 대기와 단 한 번의 선정을 검증한다. */
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
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<DrawConfirmation> first = executor.submit(() -> service.confirm(snapshot.runId()));
			try {
				assertThat(selecting.await(10, TimeUnit.SECONDS)).isTrue();
				// when
				Future<DrawConfirmation> second = executor.submit(() -> service.confirm(snapshot.runId()));
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

	/** DB FK가 허용하는 다른 이벤트의 후보 연결도 서비스의 이벤트 관계 검증으로 거절되는지 확인한다. */
	@Test
	@DisplayName("DB에서 허용되는 타 이벤트 후보 연결을 확정 전에 거절한다")
	void rejectsCandidateFromAnotherEvent() {
		// given
		addParticipant(eventId, userId);
		DrawSnapshot snapshot = snapshots.prepareInitial(eventId);
		UUID foreignEvent = addEvent();
		addPrize(foreignEvent, 1, 1);
		addParticipant(foreignEvent, addUser());
		DrawSnapshot foreign = snapshots.prepareInitial(foreignEvent);
		jdbc.update("insert into draw_run_candidates (draw_run_id, candidate_id) values (?, ?)",
				toBytes(snapshot.runId()), toBytes(foreign.candidates().getFirst().id()));
		// when / then
		assertInvalid(snapshot);
		assertReadyWithoutResults(snapshot);
	}

	/** 후보 자격 스냅샷의 사용자와 실제 참가자 사용자가 다른 경우 결과를 확정하지 않는지 검증한다. */
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

	/** 규칙 스냅샷의 경품이 실행과 다른 이벤트에 속하면 READY 상태를 유지하며 거절하는지 검증한다. */
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

	/** 유효 배정 충돌을 거절하고 취소된 배정만 제외하되 원래 결과 행은 보존하는지 검증한다. */
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

	/** 서비스 검증을 우회한 직접 JPA 저장에서 자리·후보·순서 UNIQUE와 실행 후보 복합 FK를 검증한다. */
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
		DrawSnapshot.Candidate evidence = snapshot.candidates().getFirst();
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

	/** DB 제약 자체를 검증하려고 테스트 전용 트랜잭션을 연다. 실패를 독립적으로 롤백하며 운영 업무 경계는 바꾸지 않는다. */
	private void assertJpaConstraint(DrawSnapshot snapshot, DrawSelection selection, String constraint) {
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			DrawRunEntity run = runs.findById(snapshot.runId()).orElseThrow();
			results.saveAndFlush(new DrawResultEntity(run, selection));
		})).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).hasMessageContaining(constraint);
	}
	/** 실제 서비스 호출이 INVALID_RUN으로 거절되는지 검증한다. */
	private void assertInvalid(DrawSnapshot snapshot) {
		assertThatThrownBy(() -> service.confirm(snapshot.runId())).isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(INVALID_RUN);
	}
	/** 확정 실패가 결과 행을 남기지 않고 실행을 READY로 유지하는지 DB에서 확인한다. */
	private void assertReadyWithoutResults(DrawSnapshot snapshot) {
		assertThat(runs.findById(snapshot.runId()).orElseThrow().getStatus()).isEqualTo(DrawRunStatus.READY);
		assertThat(results.findByDrawRunId(snapshot.runId())).isEmpty();
	}
	/** 각 시나리오가 독립적으로 참조할 활성 VIP 사용자를 저장한다. */
	private UUID addUser() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
			insert into users (id, name, role, status, membership, created_at, updated_at)
			values (?, '확정 테스트', 'USER', 'ACTIVE', 'VIP', ?, ?)
			""", toBytes(id), utc(NOW), utc(NOW));
		return id;
	}
	/** 검토 시간 5분이 지난 응모권 미사용 이벤트를 저장한다. */
	private UUID addEvent() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
			insert into events (id, title, description, image_key, event_type, weighting_enabled,
			starts_at, ends_at, status, membership_rule, created_at, updated_at)
			values (?, '확정 테스트', '설명', 'test.png', 'NO_TICKET', false, ?, ?, 'CLOSED', 'vip', ?, ?)
			""", toBytes(id), utc(NOW.minusSeconds(3600)), utc(NOW.minusSeconds(300)), utc(NOW), utc(NOW));
		return id;
	}
	/** 경품의 이벤트 소속·등수·자리 수를 제어할 테스트 행을 저장한다. */
	private UUID addPrize(UUID event, int rank, int count) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
			insert into event_prizes (id, event_id, prize_rank, name, winner_count, created_at, updated_at)
			values (?, ?, ?, '경품', ?, ?, ?)
			""", toBytes(id), toBytes(event), rank, count, utc(NOW), utc(NOW));
		return id;
	}
	/** 마감 전에 접수된 응모와 참가자를 함께 준비하여 스냅샷의 유효 입력을 만든다. */
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
	/** 현재 테스트 이벤트의 결과 또는 공개 버전 행만 집계하여 다른 테스트 데이터와 분리한다. */
	private long count(String table) {
		String sql = table.equals("draw_results")
				? "select count(*) from draw_results where draw_run_id in (select id from draw_runs where event_id = ?)"
				: "select count(*) from draw_publications where event_id = ?";
		return jdbc.queryForObject(sql, Long.class, toBytes(eventId));
	}
	/** 고정 Instant를 DB의 UTC DATETIME 입력으로 변환한다. */
	private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
}
