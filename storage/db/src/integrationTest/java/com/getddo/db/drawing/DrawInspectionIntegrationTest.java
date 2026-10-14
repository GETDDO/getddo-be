package com.getddo.db.drawing;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.drawing.domain.DrawInspectionView;
import com.getddo.core.drawing.domain.DrawSelectionResultType;
import com.getddo.core.drawing.domain.DrawVerification;
import com.getddo.core.drawing.service.DrawConfirmationService;
import com.getddo.core.drawing.service.DrawInspectionService;
import com.getddo.core.drawing.service.DrawSnapshotService;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.db.ticket.MutableClock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.getddo.db.common.util.UuidBinary.toBytes;
import static org.assertj.core.api.Assertions.*;

/** 실제 MySQL·기존 GD-84/85 서비스와 연동한다. 취소·공개 생성은 SQL fixture이며 GD-138/139 연동이 아니다. */
@SpringBootTest(classes = DrawSnapshotIntegrationTest.TestApplication.class,
		properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.properties.hibernate.jdbc.time_zone=UTC"})
class DrawInspectionIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
	@Autowired private JdbcTemplate jdbc;
	@Autowired private DrawSnapshotService snapshots;
	@Autowired private DrawConfirmationService confirmation;
	@Autowired private DrawInspectionService service;
	@Autowired private MutableClock clock;
	private UUID eventId, prizeId, runId;
	private User admin;

	@BeforeEach
	void setUp() {
		clock.set(NOW);
		UUID actorId = UUID.randomUUID(), userId = UUID.randomUUID(), participantId = UUID.randomUUID();
		admin = new User(actorId, "관리자", UserRole.ADMIN, UserStatus.ACTIVE, null, null, null, null, null, NOW, NOW);
		addUser(actorId, "ADMIN");
		addUser(userId, "USER");
		eventId = UUID.randomUUID(); prizeId = UUID.randomUUID();
		jdbc.update("""
			insert into events (id, title, description, event_type, weighting_enabled, starts_at, ends_at,
			status, membership_rule, created_at, updated_at)
			values (?, '조회 검증', '설명', 'NO_TICKET', false, ?, ?, 'CLOSED', 'vip', ?, ?)
			""", toBytes(eventId), utc(NOW.minusSeconds(3600)), utc(NOW.minusSeconds(300)), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into event_prizes (id, event_id, prize_rank, name, winner_count, created_at, updated_at)
			values (?, ?, 1, '경품', 2, ?, ?)
			""", toBytes(prizeId), toBytes(eventId), utc(NOW), utc(NOW));
		jdbc.update("""
			insert into event_participants (id, event_id, user_id, used_ticket_count, created_at) values (?, ?, ?, 0, ?)
			""", toBytes(participantId), toBytes(eventId), toBytes(userId), utc(NOW.minusSeconds(301)));
		jdbc.update("""
			insert into event_entries (id, event_id, participant_id, user_id, requested_ticket_count, deducted_ticket_count, created_at)
			values (?, (select event_id from event_participants where id = ?), ?, ?, 0, 0, ?)
			""", toBytes(UUID.randomUUID()), toBytes(participantId), toBytes(participantId), toBytes(userId), utc(NOW.minusSeconds(301)));
		runId = snapshots.prepareInitial(eventId).runId();
		confirmation.confirm(runId);
	}

	@Test
	@DisplayName("실제 저장 결과를 검증하고 실패 이력도 JPA로 왕복하며 원본 자료는 변경하지 않는다")
	void persistsChecksAndPreservesOriginals() {
		// given
		Map<String, Object> before = runRow();
		DrawInspectionView.Detail detail = service.detail(admin, runId);
		clock.set(NOW.plusNanos(123456789));
		// when
		DrawVerification passed = service.verify(admin, runId);
		// then
		assertThat(passed.isPassed()).isTrue();
		assertThat(passed.getId().version()).isEqualTo(7);
		Instant storedAt = jdbc.queryForObject("select checked_at from draw_checks where id = ?",
				(row, index) -> row.getObject(1, LocalDateTime.class).toInstant(ZoneOffset.UTC), toBytes(passed.getId()));
		assertThat(passed.getVerifiedAt().getNano() % 1000).isZero();
		assertThat(passed.getVerifiedAt()).isEqualTo(storedAt);
		assertThat(passed.getVerifiedBy()).isEqualTo(admin.getId());
		assertThat(service.history(admin, runId, new PageQuery(1, 20)).getItems()).hasSize(1);
		assertThat(runRow()).isEqualTo(before);
		assertThat(service.detail(admin, runId)).usingRecursiveComparison().isEqualTo(detail);
		// 저장 가중치 변조는 실패 이력으로 남기며 값을 보정하지 않는다.
		jdbc.update("update draw_candidates set weight = 2 where draw_run_id = ?", toBytes(runId));
		clock.set(NOW.plusSeconds(1));
		DrawVerification failed = service.verify(admin, runId);
		assertThat(failed.isPassed()).isFalse();
		List<DrawVerification> history = service.history(admin, runId, new PageQuery(1, 20)).getItems();
		assertThat(history).extracting(DrawVerification::getId).containsExactly(failed.getId(), passed.getId());
		assertThat(history).filteredOn(item -> item.getId().equals(failed.getId())).first().usingRecursiveComparison().isEqualTo(failed);
		assertThat(jdbc.queryForObject("select weight from draw_candidates where draw_run_id = ?", Long.class, toBytes(runId))).isEqualTo(2);
	}

	@Test
	@DisplayName("손상된 저장 JSON도 검증 실패 이력으로 보존하며 원문 예외를 노출하지 않는다")
	void malformedSnapshotBecomesFailedHistory() {
		// given
		jdbc.update("update draw_candidates set entry_snapshot = json_object() where draw_run_id = ?", toBytes(runId));
		// when
		DrawVerification check = service.verify(admin, runId);
		// then
		assertThat(check.isPassed()).isFalse();
		assertThat(check.getChecks()).anyMatch(item -> item.getCode().equals("CANDIDATE_EVIDENCE") && !item.isPassed());
		assertThat(service.history(admin, runId, new PageQuery(1, 20)).getItems()).hasSize(1);
	}

	@Test
	@DisplayName("실행별 후보 연결을 따라 최초 후보를 재사용하고 이벤트별 페이지를 분리한다")
	void pagesActualRunCandidates() {
		// given: 공통 REPLACEMENT 생성 구현이 아닌 조회용 저장 fixture
		UUID replacement = UUID.randomUUID();
		jdbc.update("""
			insert into draw_runs (id, event_id, run_number, draw_type, status, created_at)
			values (?, ?, 1, 'REPLACEMENT', 'PREPARING', ?)
			""", toBytes(replacement), toBytes(eventId), utc(NOW));
		DrawInspectionView.Candidate original = service.candidates(admin, runId, new PageQuery(1, 1)).getItems().getFirst();
		jdbc.update("insert into draw_run_candidates (draw_run_id, candidate_id) values (?, ?)", toBytes(replacement), toBytes(original.getSnapshot().id()));
		// when / then
		assertThat(service.runs(admin, eventId, new PageQuery(1, 1)).getItems()).extracting(DrawInspectionView.Run::getId).containsExactly(replacement);
		assertThat(service.runs(admin, eventId, new PageQuery(2, 1)).getItems()).extracting(DrawInspectionView.Run::getId).containsExactly(runId);
		assertThat(service.candidates(admin, replacement, new PageQuery(1, 1)).getItems()).singleElement().usingRecursiveComparison().isEqualTo(original);
		assertThat(service.candidates(admin, replacement, new PageQuery(2, 1)).getItems()).isEmpty();
		assertThat(service.run(admin, replacement).getOriginalDrawId()).isEqualTo(runId);
	}

	@Test
	@DisplayName("실행 공개만으로 결과를 공개 판정하지 않고 실제 공개 행과 취소를 분리한다")
	void publicationAndCancellationAreIndependent() {
		// given: 취소·공개 처리는 담당 서비스 대신 저장 자료 fixture만 구성한다.
		DrawInspectionView.Result selected = service.results(admin, runId).stream().filter(result -> result.getResultType() == DrawSelectionResultType.SELECTED).findFirst().orElseThrow();
		assertThat(selected.isWasPublished()).isFalse();
		assertThat(selected.isCanceled()).isFalse();
		UUID publication = UUID.randomUUID();
		jdbc.update("""
			insert into draw_publications (id, event_id, source_draw_id, revision, publication_type, published_at)
			values (?, ?, ?, 1, 'INITIAL', ?)
			""", toBytes(publication), toBytes(eventId), toBytes(runId), utc(NOW));
		jdbc.update("""
			insert into award_cancellations (id, draw_result_id, canceled_by, reason, canceled_at)
			values (?, ?, ?, '관리자 사유', ?)
			""", toBytes(UUID.randomUUID()), toBytes(selected.getId()), toBytes(admin.getId()), utc(NOW));
		// when / then: 공개 전에 취소된 결과는 공개 이력이 없다.
		DrawInspectionView.Result canceled = service.results(admin, runId).stream().filter(result -> result.getId().equals(selected.getId())).findFirst().orElseThrow();
		assertThat(canceled.isCanceled()).isTrue();
		assertThat(canceled.isWasPublished()).isFalse();
		// 이미 실제 명단에 포함된 경우에는 둘 다 true다.
		jdbc.update("insert into draw_publication_results (publication_id, draw_result_id, event_id, draw_run_id) values (?, ?, ?, ?)",
				toBytes(publication), toBytes(selected.getId()), toBytes(eventId), toBytes(runId));
		List<DrawInspectionView.Result> results = service.results(admin, runId);
		assertThat(results).filteredOn(result -> result.getId().equals(selected.getId())).allMatch(result -> result.isCanceled() && result.isWasPublished());
		assertThat(results).filteredOn(result -> result.getResultType() == DrawSelectionResultType.UNFILLED).allMatch(result -> !result.isCanceled() && !result.isWasPublished());
		assertThat(service.verify(admin, runId).isPassed()).isTrue();
	}

	private void addUser(UUID id, String role) {
		jdbc.update("insert into users (id, name, role, status, membership, created_at, updated_at) values (?, '조회 검증', ?, 'ACTIVE', 'VIP', ?, ?)",
				toBytes(id), role, utc(NOW), utc(NOW));
	}
	private Map<String, Object> runRow() {
		return jdbc.queryForMap("select status, algorithm_version, rules_snapshot, snapshot_fixed_at, started_at, confirmed_at from draw_runs where id = ?", toBytes(runId));
	}
	private static LocalDateTime utc(Instant instant) { return LocalDateTime.ofInstant(instant, ZoneOffset.UTC); }
}
