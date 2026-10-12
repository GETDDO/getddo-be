package com.getddo.db.drawing;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.db.audit.repository.AuditLogRepositoryImpl;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.exception.ErrorCode;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.*;
import com.getddo.core.drawing.service.*;
import com.getddo.db.drawing.repository.*;
import com.getddo.db.ticket.LockWaitProbe;
import com.getddo.db.ticket.MutableClock;

import static com.getddo.core.drawing.exception.AwardCancellationErrorCode.*;
import static com.getddo.core.drawing.exception.DrawingErrorCode.ADMIN_REQUIRED;
import static com.getddo.db.common.util.UuidBinary.toBytes;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 취소·등록·준비·GD-87 실제 실행을 연결한다. 응모·보상 출처만 SQL 테스트 데이터다. */
@SpringBootTest(classes = DrawSnapshotIntegrationTest.TestApplication.class,
		properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"})
class AwardCancellationFlowIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
	@Autowired private AwardCancellationService service;
	@Autowired private AwardCancellationRegistrationService registration;
	@Autowired private DrawSnapshotService snapshotService;
	@Autowired private DrawConfirmationService confirmation;
	@Autowired private DrawSnapshotRepository snapshots;
	@Autowired private DrawFailureRepository failures;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MutableClock clock;
	@Autowired private MySQLContainer mysql;
	@MockitoSpyBean private ReplacementDrawRepositoryImpl replacements;
	@MockitoSpyBean private AwardCancellationRepositoryImpl cancellations;
	@MockitoSpyBean private DrawResultJpaRepository results;
	@MockitoSpyBean private DrawingSelectionService selection;
	@MockitoSpyBean private AuditLogRepositoryImpl auditRecords;
	private UUID actor;
	private Fixture fixture;

	@BeforeEach void setUp() {
		clock.set(NOW);
		actor = user("ADMIN");
		fixture = event(5, true);
		clearInvocations(selection);
	}

	@Test @DisplayName("실제 취소·재추첨은 유지 배정·최초 가중치·응모권을 보존하고 같은 키에 결과를 재반환한다")
	void executesAndReplaysActualReplacement() {
		// given
		DrawResult target = target(fixture);
		UUID key = UUID.randomUUID();
		List<Map<String, Object>> tickets = ticketRows(fixture.eventId());
		long histories = historyCount(fixture.eventId());
		Set<UUID> winners = fixture.confirmed().getResults().stream().filter(result -> result.getSelection().getUserId() != null)
				.map(result -> result.getSelection().getUserId()).collect(Collectors.toSet());
		// when
		AwardCancellationReceipt first = service.cancel(actor, target.getId(), key, "관리자 사유");
		// then
		assertThat(first.execution().getStatus()).isEqualTo(DrawRunStatus.CONFIRMED);
		UUID run = first.cancellation().replacementRunId();
		DrawSnapshot fixed = snapshots.findRegistered(run).orElseThrow();
		assertThat(fixed.rules().version()).isEqualTo(2);
		assertThat(fixed.rules().originalRunId()).isEqualTo(fixture.original().runId());
		assertThat(fixed.candidates()).containsExactlyInAnyOrderElementsOf(fixture.original().candidates().stream()
				.filter(candidate -> !winners.contains(candidate.userId())).toList());
		assertThat(first.execution().getConfirmation().getResults()).hasSize(1);
		DrawSelection replacement = first.execution().getConfirmation().getResults().getFirst().getSelection();
		assertThat(replacement.getPrizeId()).isEqualTo(target.getSelection().getPrizeId());
		assertThat(replacement.getSlotNumber()).isEqualTo(target.getSelection().getSlotNumber());
		assertThat(winners).doesNotContain(replacement.getUserId());
		assertThat(confirmation.confirm(fixture.original().runId())).usingRecursiveComparison().isEqualTo(fixture.confirmed());
		List<UUID> retained = fixture.confirmed().getResults().stream().filter(result -> !result.getId().equals(target.getId()))
				.map(DrawResult::getId).toList();
		assertThat(replacements.findCurrentResults(fixture.eventId(), fixture.original()).stream().map(DrawResult::getId).toList())
				.containsAll(retained).hasSize(3);
		AwardCancellationReceipt replay = service.cancel(actor, target.getId(), key, " 관리자 사유 ");
		assertThat(replay).usingRecursiveComparison().isEqualTo(first);
		assertThat(service.startOrResume(actor, first.cancellation().id())).usingRecursiveComparison().isEqualTo(first.execution());
		assertThat(runCount(fixture.eventId())).isEqualTo(2);
		assertThat(cancellationCount(fixture.eventId())).isEqualTo(1);
		assertThat(ticketRows(fixture.eventId())).isEqualTo(tickets);
		assertThat(historyCount(fixture.eventId())).isEqualTo(histories);
		assertThat(jdbc.queryForObject("select status from events where id = ?", String.class, toBytes(fixture.eventId()))).isEqualTo("REDRAWING");
		assertThat(jdbc.queryForObject("select count(*) from draw_publications where event_id = ?", Long.class, toBytes(fixture.eventId()))).isZero();
		assertThat(auditActor("AWARD_CANCEL", target.getId())).isEqualTo(actor);
		assertThat(auditActor("DRAW_EXECUTE", run)).isEqualTo(actor);
		verify(selection, times(1)).select(anyList(), anyList(), anyBoolean());
		assertCode(() -> service.cancel(actor, target.getId(), key, "다른 사유"), KEY_CONFLICT);
		assertCode(() -> service.cancel(actor, target.getId(), UUID.randomUUID(), "사유"), NOT_CANCELABLE);
	}

	@Test @DisplayName("재추첨 당첨의 후속 취소도 최초 후보를 재사용하고 과거 실행 결과는 그대로 반환한다")
	void cancelsReplacementWinnerAcrossGenerations() {
		// given
		DrawResult originalTarget = target(fixture);
		AwardCancellationReceipt first = service.cancel(actor, originalTarget.getId(), UUID.randomUUID(), "첫 취소");
		DrawResult firstReplacement = first.execution().getConfirmation().getResults().getFirst();
		// when
		AwardCancellationReceipt second = service.cancel(actor, firstReplacement.getId(), UUID.randomUUID(), "후속 취소");
		DrawResult secondReplacement = second.execution().getConfirmation().getResults().getFirst();
		AwardCancellationReceipt exhausted = service.cancel(actor, secondReplacement.getId(), UUID.randomUUID(), "후보 소진 취소");
		// then
		assertThat(second.execution().getStatus()).isEqualTo(DrawRunStatus.CONFIRMED);
		assertThat(secondReplacement.getSelection().getUserId()).isNotEqualTo(firstReplacement.getSelection().getUserId());
		assertThat(exhausted.execution().getStatus()).isEqualTo(DrawRunStatus.CONFIRMED);
		assertThat(exhausted.execution().getConfirmation().getResults().getFirst().getSelection().getResultType())
				.isEqualTo(DrawSelectionResultType.UNFILLED);
		assertThat(service.startOrResume(actor, first.cancellation().id())).usingRecursiveComparison().isEqualTo(first.execution());
		assertThat(service.startOrResume(actor, second.cancellation().id())).usingRecursiveComparison().isEqualTo(second.execution());
		assertThat(runCount(fixture.eventId())).isEqualTo(4);
		assertThat(cancellationCount(fixture.eventId())).isEqualTo(3);
		assertThat(replacements.findCurrentResults(fixture.eventId(), fixture.original())).hasSize(2);
		assertThat(jdbc.queryForList("select run_number from draw_runs where event_id = ? order by run_number", Integer.class,
				toBytes(fixture.eventId()))).containsExactly(0, 1, 2, 3);
	}

	@Test @DisplayName("실행 등록 flush 뒤 실패하면 취소·실행·감사가 남지 않는다")
	void rollsBackRegistrationFailure() {
		// given
		doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("registration failure injection"); })
				.when(replacements).register(fixture.eventId());
		// when / then
		assertThatThrownBy(() -> service.cancel(actor, target(fixture).getId(), UUID.randomUUID(), "사유")).isInstanceOf(RuntimeException.class);
		assertThat(runCount(fixture.eventId())).isEqualTo(1);
		assertThat(cancellationCount(fixture.eventId())).isZero();
		assertThat(auditCount("AWARD_CANCEL", target(fixture).getId())).isZero();
		verifyNoInteractions(selection);
	}

	@Test @DisplayName("취소 flush 뒤 감사 실패도 취소·등록·이벤트 상태를 함께 롤백한다")
	void rollsBackCancellationAndAuditFailure() {
		// given
		doThrow(new IllegalStateException("audit failure injection")).when(auditRecords).append(any());
		// when / then
		assertThatThrownBy(() -> service.cancel(actor, target(fixture).getId(), UUID.randomUUID(), "사유")).isInstanceOf(RuntimeException.class);
		assertThat(runCount(fixture.eventId())).isEqualTo(1);
		assertThat(cancellationCount(fixture.eventId())).isZero();
		assertThat(jdbc.queryForObject("select status from events where id = ?", String.class, toBytes(fixture.eventId()))).isEqualTo("CLOSED");
		verifyNoInteractions(selection);
	}

	@Test @DisplayName("선정 실패는 부분 결과를 롤백하고 동일 실행·고정 입력을 다른 관리자가 재개한다")
	void resumesFailureWithoutNewCancellationOrInput() {
		// given
		UUID key = UUID.randomUUID();
		doAnswer(invocation -> { invocation.callRealMethod(); throw new TransientDataAccessResourceException("failure injection"); })
				.when(results).saveAllAndFlush(anyList());
		// when
		AwardCancellationReceipt failed = service.cancel(actor, target(fixture).getId(), key, "취소 근거");
		// then
		UUID run = failed.cancellation().replacementRunId();
		assertThat(failed.execution().getStatus()).isEqualTo(DrawRunStatus.FAILED);
		assertThat(failures.count(run)).isEqualTo(1);
		assertThat(resultCount(run)).isZero();
		DrawSnapshot fixed = snapshots.findRegistered(run).orElseThrow();
		assertThat(fixed.fixedAt()).isNotNull();
		assertThat(service.cancel(actor, target(fixture).getId(), key, "취소 근거").execution().getStatus()).isEqualTo(DrawRunStatus.FAILED);
		reset(results);
		jdbc.update("update event_participants set used_ticket_count = 99 where event_id = ?", toBytes(fixture.eventId()));
		UUID resumer = user("ADMIN");
		DrawExecutionResult resumed = service.startOrResume(resumer, failed.cancellation().id());
		assertThat(resumed.getStatus()).isEqualTo(DrawRunStatus.CONFIRMED);
		DrawSnapshot after = snapshots.findRegistered(run).orElseThrow();
		assertThat(after.candidates()).isEqualTo(fixed.candidates());
		assertThat(after.rules()).isEqualTo(fixed.rules());
		assertThat(after.fixedAt()).isEqualTo(fixed.fixedAt());
		assertThat(runCount(fixture.eventId())).isEqualTo(2);
		assertThat(cancellationCount(fixture.eventId())).isEqualTo(1);
		assertThat(auditActor("AWARD_CANCEL", target(fixture).getId())).isEqualTo(actor);
		assertThat(auditActor("DRAW_RESUME", run)).isEqualTo(resumer);
		assertThat(service.startOrResume(resumer, failed.cancellation().id())).usingRecursiveComparison().isEqualTo(resumed);
	}

	@Test @DisplayName("미확정 재추첨이 있는 동안 새 취소는 거절하고 기존 취소의 시작은 같은 실행을 사용한다")
	void rejectsNewCancellationDuringPendingReplacement() {
		// given
		DrawResult target = target(fixture);
		UUID key = UUID.randomUUID();
		AwardCancellation registered = registration.register(actor, target.getId(), key, "사유");
		DrawResult other = fixture.confirmed().getResults().stream().filter(result -> !result.getId().equals(target.getId())).findFirst().orElseThrow();
		// when / then
		assertCode(() -> service.cancel(actor, other.getId(), UUID.randomUUID(), "다른 취소"), NOT_CANCELABLE);
		assertThat(registration.register(actor, target.getId(), key, "사유")).isEqualTo(registered);
		DrawExecutionResult started = service.startOrResume(actor, registered.id());
		assertThat(started.getRunId()).isEqualTo(registered.replacementRunId());
		assertThat(started.getStatus()).isEqualTo(DrawRunStatus.CONFIRMED);
		assertThat(runCount(fixture.eventId())).isEqualTo(2);
		assertThat(cancellationCount(fixture.eventId())).isEqualTo(1);
	}

	@Test @DisplayName("후보가 없으면 실제 취소 자리만 UNFILLED로 확정하고 기존 미충원 자리를 추가로 추첨하지 않는다")
	void confirmsOnlyVacantSlotWithoutCandidates() {
		// given
		Fixture insufficient = event(1, false);
		DrawResult target = insufficient.confirmed().getResults().getFirst();
		clearInvocations(selection);
		// when
		AwardCancellationReceipt receipt = service.cancel(actor, target.getId(), UUID.randomUUID(), "사유");
		// then
		assertThat(receipt.execution().getStatus()).isEqualTo(DrawRunStatus.CONFIRMED);
		List<DrawResult> stored = receipt.execution().getConfirmation().getResults();
		assertThat(stored).hasSize(1);
		assertThat(stored.getFirst().getSelection().getSlotNumber()).isEqualTo(target.getSelection().getSlotNumber());
		assertThat(stored.getFirst().getSelection().getResultType()).isEqualTo(DrawSelectionResultType.UNFILLED);
		verifyNoInteractions(selection);
		DrawResult oldUnfilled = insufficient.confirmed().getResults().getLast();
		assertCode(() -> service.cancel(actor, oldUnfilled.getId(), UUID.randomUUID(), "사유"), NOT_CANCELABLE);
	}

	@Test @DisplayName("동시 AD05 등록이 실제 이벤트 잠금에서 대기하고 취소·실행을 한 번만 등록한다")
	void concurrentRegistrationUsesOneCancellationAndRun() throws Exception {
		// given
		UUID key = UUID.randomUUID();
		CountDownLatch registering = new CountDownLatch(1), release = new CountDownLatch(1);
		doAnswer(invocation -> {
			Object registered = invocation.callRealMethod();
			registering.countDown();
			if (!release.await(45, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
			return registered;
		}).when(replacements).register(fixture.eventId());
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<AwardCancellationReceipt> first = executor.submit(() -> service.cancel(actor, target(fixture).getId(), key, "사유"));
			try {
				assertThat(registering.await(20, TimeUnit.SECONDS)).isTrue();
				// when
				Future<AwardCancellationReceipt> second = executor.submit(() -> service.cancel(actor, target(fixture).getId(), key, "사유"));
				new LockWaitProbe(mysql).awaitLockWaits(1);
				assertThat(second.isDone()).isFalse();
				release.countDown();
				// then
				AwardCancellationReceipt one = first.get(30, TimeUnit.SECONDS), two = second.get(30, TimeUnit.SECONDS);
				assertThat(one.cancellation()).isEqualTo(two.cancellation());
				assertThat(service.startOrResume(actor, one.cancellation().id()).getStatus()).isEqualTo(DrawRunStatus.CONFIRMED);
			} finally { release.countDown(); }
		}
		assertThat(runCount(fixture.eventId())).isEqualTo(2);
		assertThat(cancellationCount(fixture.eventId())).isEqualTo(1);
		verify(selection, times(1)).select(anyList(), anyList(), anyBoolean());
	}

	@Test @DisplayName("AD05 자동 시작·재요청과 AD09가 실제 이벤트 잠금에서 경합해 결과를 한 번만 확정한다")
	void concurrentAutoStartReplayAndAd09SelectOnce() throws Exception {
		// given
		UUID key = UUID.randomUUID();
		CountDownLatch selecting = new CountDownLatch(1), release = new CountDownLatch(1);
		doAnswer(invocation -> { selecting.countDown(); if (!release.await(45, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
			return invocation.callRealMethod(); }).when(selection).select(anyList(), anyList(), anyBoolean());
		try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
			Future<AwardCancellationReceipt> first = executor.submit(() -> service.cancel(actor, target(fixture).getId(), key, "사유"));
			try {
				assertThat(selecting.await(20, TimeUnit.SECONDS)).isTrue();
				AwardCancellation connected = cancellations.findByRequestKey(key).orElseThrow();
				// when
				Future<DrawExecutionResult> ad09 = executor.submit(() -> service.startOrResume(actor, connected.id()));
				Future<AwardCancellationReceipt> replay = executor.submit(() -> service.cancel(actor, target(fixture).getId(), key, "사유"));
				new LockWaitProbe(mysql).awaitLockWaits(2);
				assertThat(ad09.isDone()).isFalse();
				release.countDown();
				// then
				AwardCancellationReceipt confirmed = first.get(30, TimeUnit.SECONDS);
				assertThat(ad09.get(30, TimeUnit.SECONDS)).usingRecursiveComparison().isEqualTo(confirmed.execution());
				assertThat(replay.get(30, TimeUnit.SECONDS)).usingRecursiveComparison().isEqualTo(confirmed);
			} finally { release.countDown(); }
		}
		assertThat(runCount(fixture.eventId())).isEqualTo(2);
		assertThat(cancellationCount(fixture.eventId())).isEqualTo(1);
		verify(selection, times(1)).select(anyList(), anyList(), anyBoolean());
	}

	@Test @DisplayName("같은 전역 키로 다른 이벤트를 동시 취소하면 한 등록만 남고 다른 등록은 롤백된다")
	void concurrentGlobalKeyCollisionRollsBackOtherEvent() throws Exception {
		// given
		Fixture other = event(4, false);
		UUID key = UUID.randomUUID();
		CountDownLatch storing = new CountDownLatch(2), release = new CountDownLatch(1);
		doAnswer(invocation -> { storing.countDown(); if (!release.await(45, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
			return invocation.callRealMethod(); }).when(cancellations).save(any());
		try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
			Future<Object> first = executor.submit(() -> cancelOrConflict(target(fixture).getId(), key));
			Future<Object> second = executor.submit(() -> cancelOrConflict(target(other).getId(), key));
			try {
				assertThat(storing.await(20, TimeUnit.SECONDS)).isTrue();
				release.countDown();
				// when / then
				List<Object> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
				assertThat(outcomes.stream().filter(AwardCancellationReceipt.class::isInstance)).hasSize(1);
				assertThat(outcomes).contains(KEY_CONFLICT);
			} finally { release.countDown(); }
		}
		assertThat(runCount(fixture.eventId()) + runCount(other.eventId())).isEqualTo(3);
		assertThat(cancellationCount(fixture.eventId()) + cancellationCount(other.eventId())).isEqualTo(1);
	}

	@Test @DisplayName("실제 비관리자·비활성 관리자·없는 대상·취소 이벤트는 취소와 실행을 만들지 않는다")
	void rejectsUnauthorizedAndInvalidTargets() {
		// given / when / then
		assertCode(() -> service.cancel(user("USER"), target(fixture).getId(), UUID.randomUUID(), "사유"), ADMIN_REQUIRED);
		UUID inactive = user("ADMIN");
		jdbc.update("update users set status = 'INACTIVE' where id = ?", toBytes(inactive));
		assertCode(() -> service.cancel(inactive, target(fixture).getId(), UUID.randomUUID(), "사유"), ADMIN_REQUIRED);
		assertCode(() -> service.cancel(actor, UUID.randomUUID(), UUID.randomUUID(), "사유"), NOT_FOUND);
		assertCode(() -> service.startOrResume(actor, UUID.randomUUID()), NOT_FOUND);
		jdbc.update("update events set status = 'CANCELED' where id = ?", toBytes(fixture.eventId()));
		assertCode(() -> service.cancel(actor, target(fixture).getId(), UUID.randomUUID(), "사유"), NOT_CANCELABLE);
		assertThat(runCount(fixture.eventId())).isEqualTo(1);
		assertThat(cancellationCount(fixture.eventId())).isZero();
	}

	private Object cancelOrConflict(UUID win, UUID key) {
		try { return service.cancel(actor, win, key, "사유"); }
		catch (BusinessException exception) { return exception.getErrorCode(); }
	}
	private Fixture event(int entrants, boolean weighted) {
		UUID event = UUID.randomUUID(), firstPrize = UUID.randomUUID();
		jdbc.update("""
			insert into events (id, title, description, image_key, event_type, weighting_enabled, starts_at, ends_at,
			status, membership_rule, created_at, updated_at) values (?, '취소 연동', '설명', 'test.png', ?, ?, ?, ?, 'CLOSED', 'vip', ?, ?)
			""", toBytes(event), weighted ? "TICKET" : "NO_TICKET", weighted, utc(NOW.minusSeconds(3600)), utc(NOW.minusSeconds(300)), utc(NOW), utc(NOW));
		for (int rank = 1; rank <= 2; rank++) jdbc.update("""
			insert into event_prizes (id, event_id, prize_rank, name, winner_count, created_at, updated_at)
			values (?, ?, ?, '경품', ?, ?, ?)
			""", toBytes(rank == 1 ? firstPrize : UUID.randomUUID()), toBytes(event), rank, rank == 1 ? 2 : 1, utc(NOW), utc(NOW));
		for (int index = 0; index < entrants; index++) {
			UUID user = user("USER"), participant = UUID.randomUUID(), entry = UUID.randomUUID();
			int count = weighted ? 1 : 0;
			jdbc.update("insert into event_participants (id, event_id, user_id, used_ticket_count, created_at) values (?, ?, ?, ?, ?)",
					toBytes(participant), toBytes(event), toBytes(user), count, utc(NOW.minusSeconds(301)));
			jdbc.update("insert into event_entries (id, participant_id, user_id, requested_ticket_count, deducted_ticket_count, created_at) values (?, ?, ?, ?, ?, ?)",
					toBytes(entry), toBytes(participant), toBytes(user), count, count, utc(NOW.minusSeconds(301)));
			if (weighted) ticket(user, entry, List.of("GOLD", "SILVER", "BRONZE").get(index % 3));
		}
		DrawSnapshot original = snapshotService.prepareInitial(event);
		return new Fixture(event, original, confirmation.confirm(original.runId()));
	}
	private void ticket(UUID owner, UUID entry, String grade) {
		UUID policy = UUID.randomUUID(), mission = UUID.randomUUID(), submission = UUID.randomUUID(), claim = UUID.randomUUID(), ticket = UUID.randomUUID();
		jdbc.update("insert into reward_policies (id, created_by, reward_type, reward_ticket_count, effective_from, created_at) values (?, ?, 'MISSION', 1, ?, ?)",
				toBytes(policy), toBytes(actor), utc(NOW.minusSeconds(3600)), utc(NOW.minusSeconds(3600)));
		jdbc.update("""
			insert into missions (id, reward_policy_id, created_by, title, description, mission_type, starts_at, ends_at, status, created_at, updated_at)
			values (?, ?, ?, '연동 출처', '설명', 'QUIZ', ?, ?, 'ACTIVE', ?, ?)
			""", toBytes(mission), toBytes(policy), toBytes(actor), utc(NOW.minusSeconds(3600)), utc(NOW), utc(NOW), utc(NOW));
		jdbc.update("insert into mission_submissions (id, mission_id, reward_policy_id, user_id, is_completed, created_at) values (?, ?, ?, ?, true, ?)",
				toBytes(submission), toBytes(mission), toBytes(policy), toBytes(owner), utc(NOW.minusSeconds(1000)));
		jdbc.update("""
			insert into mission_reward_claims (id, reward_policy_id, mission_id, mission_submission_id, user_id, source_key, ticket_count, created_at)
			values (?, ?, ?, ?, ?, ?, 1, ?)
			""", toBytes(claim), toBytes(policy), toBytes(mission), toBytes(submission), toBytes(owner), claim.toString(), utc(NOW.minusSeconds(1000)));
		jdbc.update("""
			insert into tickets (id, user_id, mission_reward_claim_id, grade, status, expires_at, version, created_at, updated_at)
			values (?, ?, ?, ?, 'SPENT', ?, 2, ?, ?)
			""", toBytes(ticket), toBytes(owner), toBytes(claim), grade, utc(NOW.plusSeconds(3600)), utc(NOW.minusSeconds(1000)), utc(NOW.minusSeconds(301)));
		jdbc.update("""
			insert into ticket_histories (id, ticket_id, event_entry_id, operation_type, ticket_version, status, expires_at, reason, created_at)
			values (?, ?, ?, 'USE', 2, 'SPENT', ?, '테스트 응모', ?)
			""", toBytes(UUID.randomUUID()), toBytes(ticket), toBytes(entry), utc(NOW.plusSeconds(3600)), utc(NOW.minusSeconds(301)));
	}
	private UUID user(String role) {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into users (id, name, role, status, membership, created_at, updated_at) values (?, '연동 사용자', ?, 'ACTIVE', 'VIP', ?, ?)",
				toBytes(id), role, utc(NOW), utc(NOW));
		return id;
	}
	private DrawResult target(Fixture value) {
		return value.confirmed().getResults().stream().filter(result -> result.getSelection().getRank() == 1 && result.getSelection().getSlotNumber() == 2)
				.findFirst().orElseThrow();
	}
	private long runCount(UUID event) { return jdbc.queryForObject("select count(*) from draw_runs where event_id = ?", Long.class, toBytes(event)); }
	private long cancellationCount(UUID event) { return jdbc.queryForObject("select count(*) from award_cancellations where draw_run_id in (select id from draw_runs where event_id = ?)", Long.class, toBytes(event)); }
	private long resultCount(UUID run) { return jdbc.queryForObject("select count(*) from draw_results where draw_run_id = ?", Long.class, toBytes(run)); }
	private long auditCount(String action, UUID target) { return jdbc.queryForObject("select count(*) from audit_logs where action = ? and target_id = ?", Long.class, action, toBytes(target)); }
	private UUID auditActor(String action, UUID target) { return com.getddo.db.common.util.UuidBinary.fromBytes(jdbc.queryForObject("select actor_id from audit_logs where action = ? and target_id = ?", byte[].class, action, toBytes(target))); }
	private long historyCount(UUID event) { return jdbc.queryForObject("select count(*) from ticket_histories h join event_entries e on e.id = h.event_entry_id join event_participants p on p.id = e.participant_id where p.event_id = ?", Long.class, toBytes(event)); }
	private List<Map<String, Object>> ticketRows(UUID event) {
		return jdbc.queryForList("select hex(t.id) as id, t.status, t.version, t.grade from tickets t join ticket_histories h on h.ticket_id = t.id join event_entries e on e.id = h.event_entry_id join event_participants p on p.id = e.participant_id where p.event_id = ? order by t.id", toBytes(event));
	}
	private static void assertCode(Runnable operation, ErrorCode code) {
		assertThatThrownBy(operation::run).isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
	}
	private static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
	private record Fixture(UUID eventId, DrawSnapshot original, DrawConfirmation confirmed) { }
}
