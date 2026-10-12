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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.service.DrawConfirmationService;
import com.getddo.core.drawing.service.DrawPublicationService;
import com.getddo.core.drawing.service.DrawSnapshotService;
import com.getddo.db.drawing.repository.DrawPublicationRepositoryImpl;
import com.getddo.db.drawing.repository.DrawPublicationResultJpaRepository;
import com.getddo.db.drawing.repository.DrawPublicationJpaRepository;
import com.getddo.db.drawing.repository.DrawResultConfirmationJpaRepository;
import com.getddo.db.drawing.entity.DrawPublicationEntity;
import com.getddo.db.drawing.entity.DrawResultConfirmationEntity;
import com.getddo.db.ticket.LockWaitProbe;
import com.getddo.db.ticket.MutableClock;

import static com.getddo.db.common.util.UuidBinary.toBytes;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 최초 결과는 실제 GD-84·85 서비스로 준비·확정한다. 취소는 저장 이력 검증용 SQL fixture다. */
@SpringBootTest(classes = DrawSnapshotIntegrationTest.TestApplication.class,
		properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"})
class DrawPublicationIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
	@Autowired private DrawPublicationService service;
	@Autowired private DrawSnapshotService snapshots;
	@Autowired private DrawConfirmationService confirmation;
	@Autowired private MutableClock clock;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private MySQLContainer mysql;
	@Autowired private PlatformTransactionManager transactionManager;
	@Autowired private EntityManager entityManager;
	@Autowired private DrawPublicationJpaRepository versions;
	@Autowired private DrawResultConfirmationJpaRepository confirmations;
	@MockitoSpyBean private DrawPublicationRepositoryImpl repository;
	@MockitoSpyBean private DrawPublicationResultJpaRepository entries;
	private UUID eventId, userId, prizeId;
	private DrawSnapshot snapshot;
	private DrawConfirmation confirmed;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		userId = UUID.randomUUID(); eventId = UUID.randomUUID(); prizeId = UUID.randomUUID();
		jdbc.update("""
			insert into users (id, name, role, status, membership, created_at, updated_at)
			values (?, '공개 테스트', 'ADMIN', 'ACTIVE', 'VIP', ?, ?)
			""", toBytes(userId), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into events (id, title, description, image_key, event_type, weighting_enabled,
			starts_at, ends_at, status, membership_rule, created_at, updated_at)
			values (?, '공개 테스트', '설명', 'test.png', 'NO_TICKET', false, ?, ?, 'CLOSED', 'vip', ?, ?)
			""", toBytes(eventId), utc(NOW.minusSeconds(3600)), utc(NOW.minusSeconds(300)), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into event_prizes (id, event_id, prize_rank, name, winner_count, created_at, updated_at)
			values (?, ?, 1, '경품', 2, ?, ?)
			""", toBytes(prizeId), toBytes(eventId), utc(NOW), utc(NOW));
		UUID participantId = UUID.randomUUID();
		jdbc.update("insert into event_participants (id, event_id, user_id, used_ticket_count, created_at) values (?, ?, ?, 0, ?)",
				toBytes(participantId), toBytes(eventId), toBytes(userId), utc(NOW.minusSeconds(301)));
		jdbc.update("""
			insert into event_entries (id, participant_id, user_id, requested_ticket_count, deducted_ticket_count, created_at)
			values (?, ?, ?, 0, 0, ?)
			""", toBytes(UUID.randomUUID()), toBytes(participantId), toBytes(userId), utc(NOW.minusSeconds(301)));
		snapshot = snapshots.prepareInitial(eventId);
		confirmed = confirmation.confirm(snapshot.runId());
	}

	@Test
	@DisplayName("실제 최초 확정 결과를 공개하고 JPA 왕복·DB 시각·반복 공개를 보존한다")
	void publishesAndReplaysStoredVersion() {
		// given
		clock.set(NOW.plusNanos(123456789));
		UUID selectedId = selectedId();
		UUID unfilledId = confirmed.getResults().stream()
				.filter(result -> result.getSelection().getResultType() == DrawSelectionResultType.UNFILLED)
				.findFirst().orElseThrow().getId();
		assertThat(service.wasPublished(selectedId)).isFalse();
		// when
		DrawPublication first = service.publishInitial(eventId).orElseThrow();
		clock.set(NOW.plusSeconds(90));
		DrawPublication replay = service.publishInitial(eventId).orElseThrow();
		// then
		assertThat(first.id().version()).isEqualTo(7);
		assertThat(first.publishedAt().getNano() % 1000).isZero();
		assertThat(first.confirmedBy()).isNull();
		assertThat(first.entries()).containsExactly(new DrawPublication.Entry(selectedId, snapshot.runId(), null));
		assertThat(replay).isEqualTo(first);
		assertThat(service.history(eventId)).containsExactly(first);
		assertThat(service.wasPublished(selectedId)).isTrue();
		assertThat(service.wasPublished(unfilledId)).isFalse();
		assertThat(count()).isEqualTo(1);
	}

	@Test
	@DisplayName("목록 저장 실패는 공개 버전도 롤백하고 같은 최초 공개를 재시도한다")
	void rollsBackVersionWhenEntriesFail() {
		// given
		doThrow(new IllegalStateException("공개 목록 저장 실패")).when(entries).saveAllAndFlush(any());
		// when / then
		assertThatThrownBy(() -> service.publishInitial(eventId))
				.isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
				.hasRootCauseInstanceOf(IllegalStateException.class);
		assertThat(count()).isZero();
		assertThat(service.wasPublished(selectedId())).isFalse();
		// Spring Data 인터페이스 spy의 실제 위임을 복원한다. 추상 메서드에 callRealMethod를 지정하지 않는다.
		reset(entries);
		assertThat(service.publishInitial(eventId).orElseThrow().revision()).isEqualTo(1);
		assertThat(count()).isEqualTo(1);
	}

	@Test
	@DisplayName("공개 후 취소되어도 실제 공개 기록은 true이고 원본 버전은 보존된다")
	void preservesHistoryAfterCancellation() {
		// given
		DrawPublication first = service.publishInitial(eventId).orElseThrow();
		// when
		cancelSelected();
		// then
		assertThat(service.wasPublished(selectedId())).isTrue();
		assertThat(service.history(eventId)).containsExactly(first);
		assertThat(service.publishInitial(eventId)).contains(first);
		assertThat(count()).isEqualTo(1);
	}

	@Test
	@DisplayName("최초 공개 전 취소 기록은 공개 이력으로 계산하지 않고 전체 최초 공개를 지연한다")
	void delaysPublicationForCancellationFixture() {
		// given
		cancelSelected();
		// when / then
		assertThat(service.publishInitial(eventId)).isEmpty();
		assertThat(service.wasPublished(selectedId())).isFalse();
		assertThat(count()).isZero();
	}

	@Test
	@DisplayName("재추첨 확인·미충원 갱신 저장 fixture에서 확인 시각·공개 시각·이전 공개를 구분한다")
	void mapsConfirmationAndUpdateHistoryFixture() {
		// given: GD-87·138의 실제 등록·재추첨·확인 서비스 연결을 검증하는 테스트가 아니다.
		DrawPublication first = service.publishInitial(eventId).orElseThrow();
		cancelSelected();
		UUID replacementRun = UUID.randomUUID();
		jdbc.update("""
			insert into draw_runs (id, event_id, run_number, draw_type, status, algorithm_version,
			rules_snapshot, snapshot_fixed_at, confirmed_at, created_at)
			select ?, event_id, 1, 'REPLACEMENT', 'CONFIRMED', algorithm_version,
			rules_snapshot, snapshot_fixed_at, confirmed_at, created_at from draw_runs where id = ?
			""", toBytes(replacementRun), toBytes(snapshot.runId()));
		Instant checkedAt = NOW.plusSeconds(1), publishedAt = NOW.plusSeconds(2);
		DrawResultConfirmation checked = new TransactionTemplate(transactionManager).execute(status -> {
			DrawResultConfirmationEntity saved = confirmations.saveAndFlush(
					new DrawResultConfirmationEntity(replacementRun, userId, checkedAt));
			UUID confirmationId = saved.getId();
			entityManager.detach(saved);
			return confirmations.findById(confirmationId).orElseThrow().toDomain();
		});
		// when: 보충 후보 없음으로 공개 목록이 비는 버전의 저장 매핑을 확인한다.
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			versions.saveAndFlush(new DrawPublicationEntity(new DrawPublication(UUID.randomUUID(), eventId,
					replacementRun, 2, DrawPublicationType.UPDATE, "미충원 저장 fixture", userId,
					checkedAt, publishedAt, List.of())));
		});
		// then
		assertThat(checked.id().version()).isEqualTo(7);
		assertThat(confirmations.findByRunId(replacementRun).orElseThrow().toDomain()).isEqualTo(checked);
		List<DrawPublication> history = service.history(eventId);
		assertThat(history).hasSize(2);
		assertThat(history.getFirst()).isEqualTo(first);
		assertThat(history.getLast().confirmedAt()).isEqualTo(checkedAt);
		assertThat(history.getLast().publishedAt()).isEqualTo(publishedAt);
		assertThat(history.getLast().reason()).isEqualTo("미충원 저장 fixture");
		assertThat(service.wasPublished(selectedId())).isTrue();
		assertThat(service.changes(eventId).getLast().changes()).containsExactly(
				new DrawPublicationChange.SlotChange(prizeId, 1, selectedId(), userId, null, null));
		assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
				confirmations.saveAndFlush(new DrawResultConfirmationEntity(replacementRun, userId, checkedAt))))
				.isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
		assertThat(confirmations.findByRunId(replacementRun).orElseThrow().toDomain()).isEqualTo(checked);
	}

	@Test
	@DisplayName("실제 이벤트 잠금 경합에서도 동시 최초 공개는 동일한 한 버전만 반환한다")
	void concurrentRequestsPublishOnce() throws Exception {
		// given
		CountDownLatch saved = new CountDownLatch(1), release = new CountDownLatch(1);
		doAnswer(invocation -> {
			DrawPublication publication = (DrawPublication) invocation.callRealMethod();
			saved.countDown();
			if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("공개 트랜잭션 해제 대기 실패");
			return publication;
		}).when(repository).saveInitial(eq(eventId), any(), anyList(), any());
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			// when
			Future<DrawPublication> first = executor.submit(() -> service.publishInitial(eventId).orElseThrow());
			assertThat(saved.await(30, TimeUnit.SECONDS)).isTrue();
			Future<DrawPublication> second = executor.submit(() -> service.publishInitial(eventId).orElseThrow());
			new LockWaitProbe(mysql).awaitLockWaits(1);
			release.countDown();
			// then
			assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(first.get(30, TimeUnit.SECONDS));
			assertThat(count()).isEqualTo(1);
			verify(repository, times(1)).saveInitial(eq(eventId), any(), anyList(), any());
		} finally {
			release.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		}
	}

	private UUID selectedId() {
		return confirmed.getResults().stream().filter(result -> result.getSelection().getResultType() == DrawSelectionResultType.SELECTED)
				.findFirst().orElseThrow().getId();
	}
	/** 실제 GD-138 서비스 연결 없이 과거 공개 이력 보존만 검증하는 fixture다. */
	private void cancelSelected() {
		jdbc.update("""
			insert into award_cancellations (id, draw_result_id, canceled_by, reason, canceled_at)
			values (?, ?, ?, '관리자 취소 저장 fixture', ?)
			""", toBytes(UUID.randomUUID()), toBytes(selectedId()), toBytes(userId), utc(NOW));
	}
	private long count() {
		return jdbc.queryForObject("select count(*) from draw_publications where event_id = ?", Long.class, toBytes(eventId));
	}
	private static LocalDateTime utc(Instant value) { return LocalDateTime.ofInstant(value, ZoneOffset.UTC); }
}
