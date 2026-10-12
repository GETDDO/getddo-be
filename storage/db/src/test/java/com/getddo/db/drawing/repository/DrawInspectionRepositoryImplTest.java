package com.getddo.db.drawing.repository;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import com.getddo.core.drawing.domain.*;
import com.getddo.db.drawing.mapper.DrawInspectionRulesMapper;
import static com.getddo.db.common.util.UuidBinary.toBytes;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** JDBC 대역으로 SQL 조건·바인딩·행 변환을 검증한다. 실제 MySQL 실행 결과로 표현하지 않는다. */
class DrawInspectionRepositoryImplTest {
	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final DrawInspectionRepositoryImpl repository = new DrawInspectionRepositoryImpl(jdbc, new DrawInspectionRulesMapper());
	private final UUID runId = UUID.randomUUID(), eventId = UUID.randomUUID(), originalId = UUID.randomUUID();
	private final Instant fixedAt = Instant.parse("2026-10-12T03:00:00Z");

	@Test
	@DisplayName("과거 배정 조회는 이전 차수·입력 고정 시각·확정 결과로 제한하고 현재 취소 상태를 필터링하지 않는다")
	void readsHistoricalAssignmentsAtInputTime() {
		// given
		DrawRunView original = run(originalId, DrawType.INITIAL, null, fixedAt.minusSeconds(60));
		DrawRunView run = run(runId, DrawType.REPLACEMENT, originalId, fixedAt);
		when(jdbc.query(contains("where run.id = ?"), org.mockito.ArgumentMatchers.<RowMapper<DrawRunView>>any(), eq(toBytes(originalId))))
				.thenReturn(List.of(original));
		when(jdbc.query(contains("where link.draw_run_id = ?"), org.mockito.ArgumentMatchers.<RowMapper<DrawCandidateView>>any(), eq(toBytes(originalId))))
				.thenReturn(List.of());
		LocalDateTime cutoff = LocalDateTime.ofInstant(fixedAt, ZoneOffset.UTC);
		when(jdbc.query(contains("run.run_number < ?"), org.mockito.ArgumentMatchers.<RowMapper<DrawHistoricalAward>>any(), eq(toBytes(eventId)), eq(2), eq(cutoff)))
				.thenReturn(List.of());
		// when
		DrawVerificationReferences references = repository.findReplacementReferences(run);
		// then
		assertThat(references.getOriginalRun()).isSameAs(original);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<DrawHistoricalAward>>any(), eq(toBytes(eventId)), eq(2), eq(cutoff));
		assertThat(sql.getValue()).contains("run.run_number < ?", "run.confirmed_at <= ?", "run.status = 'CONFIRMED'", "result.result_type = 'SELECTED'")
				.doesNotContain("award_cancellations", "draw_publications");
	}

	@Test
	@DisplayName("실제 행 변환은 버전 2 JSON과 최초 실행 참조를 함께 보존한다")
	void mapsReplacementRunRow() throws Exception {
		// given
		UUID prizeId = UUID.randomUUID();
		ResultSet row = mock(ResultSet.class);
		when(row.getBytes("id")).thenReturn(toBytes(runId));
		when(row.getBytes("event_id")).thenReturn(toBytes(eventId));
		when(row.getBytes("original_id")).thenReturn(toBytes(originalId));
		when(row.getInt("run_number")).thenReturn(2);
		when(row.getString("draw_type")).thenReturn("REPLACEMENT");
		when(row.getString("status")).thenReturn("CONFIRMED");
		when(row.getString("rules_snapshot")).thenReturn("""
			{"version":2,"weightingEnabled":false,"eventType":"NO_TICKET","membershipRule":"ALL",
			"bronzeWeight":1,"silverWeight":3,"goldWeight":5,"prizes":[{"id":"%s","rank":1,"winnerCount":3}],
			"originalRunId":"%s","targetSlots":[{"prizeId":"%s","rank":1,"slotNumber":3}],"retainedAwards":[]}
			""".formatted(prizeId, originalId, prizeId));
		when(row.getObject("snapshot_fixed_at", LocalDateTime.class)).thenReturn(LocalDateTime.ofInstant(fixedAt, ZoneOffset.UTC));
		when(jdbc.query(contains("where run.id = ?"), org.mockito.ArgumentMatchers.<RowMapper<DrawRunView>>any(), eq(toBytes(runId))))
				.thenAnswer(invocation -> {
					RowMapper<DrawRunView> mapper = invocation.getArgument(1);
					return List.of(mapper.mapRow(row, 0));
				});
		// when
		DrawRunView run = repository.findRun(runId).orElseThrow();
		// then
		assertThat(run.getOriginalDrawId()).isEqualTo(originalId);
		assertThat(run.getReplacementRules().getOriginalRunId()).isEqualTo(originalId);
		assertThat(run.getReplacementRules().getTargetSlots().getFirst().getSlotNumber()).isEqualTo(3);
		assertThat(run.getRules().version()).isEqualTo(2);
		assertThat(run.getSnapshotFixedAt()).isEqualTo(fixedAt);
	}

	@Test
	@DisplayName("원본 실행 참조가 없으면 임의 최초 실행을 찾아 검증 근거를 만들어내지 않는다")
	void missingOriginalReturnsMissingEvidence() {
		// given / when
		DrawVerificationReferences references = repository.findReplacementReferences(run(runId, DrawType.REPLACEMENT, null, fixedAt));
		// then
		assertThat(references.getOriginalRun()).isNull();
		assertThat(references.getOriginalCandidates()).isEmpty();
		assertThat(references.getHistoricalAwards()).isEmpty();
		verifyNoInteractions(jdbc);
	}

	@Test
	@DisplayName("결과 조회는 당첨자 ID만 조인하고 미충원도 유지하며 등수·자리 순서를 사용한다")
	void resultsJoinWinnerAndPreserveUnfilled() throws Exception {
		UUID winner = UUID.randomUUID();
		ResultSet row = mock(ResultSet.class);
		when(row.getBytes("user_id")).thenReturn(toBytes(winner));
		when(row.getString("result_type")).thenReturn("SELECTED");
		when(jdbc.query(contains("select result.*"), org.mockito.ArgumentMatchers.<RowMapper<DrawResultView>>any(), eq(toBytes(runId))))
				.thenAnswer(invocation -> {
					RowMapper<DrawResultView> mapper = invocation.getArgument(1);
					DrawResultView selected = mapper.mapRow(row, 0);
					when(row.getBytes("user_id")).thenReturn(null);
					when(row.getString("result_type")).thenReturn("UNFILLED");
					return List.of(selected, mapper.mapRow(row, 1));
				});
		List<DrawResultView> results = repository.findResults(runId);
		assertThat(results).hasSize(2);
		assertThat(results.getFirst().getUserId()).isEqualTo(winner);
		assertThat(results.getLast().getUserId()).isNull();
		assertThat(results.getLast().getResultType()).isEqualTo(DrawSelectionResultType.UNFILLED);
		ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
		verify(jdbc).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<DrawResultView>>any(), eq(toBytes(runId)));
		assertThat(sql.getValue()).contains("left join draw_candidates", "left join event_participants", "order by prize.prize_rank, result.slot_number")
				.doesNotContain("entry_snapshot", "eligibility_snapshot");
	}

	@Test
	@DisplayName("null 응모 목록은 Jackson 생성자 오류로 처리돼 조회에서 손상 근거 null로 보존된다")
	void nullEntryArrayDoesNotAbortCandidateRead() throws Exception {
		ResultSet row = mock(ResultSet.class);
		when(row.getString("entry_snapshot")).thenReturn("{\"version\":1,\"bronzeCount\":0,\"silverCount\":0,\"goldCount\":0,\"entries\":null}");
		when(jdbc.query(contains("where link.draw_run_id = ?"), org.mockito.ArgumentMatchers.<RowMapper<DrawCandidateView>>any(), eq(toBytes(runId))))
				.thenAnswer(invocation -> {
					RowMapper<DrawCandidateView> mapper = invocation.getArgument(1);
					return List.of(mapper.mapRow(row, 0));
				});
		assertThat(repository.findAllCandidates(runId).getFirst().getSnapshot().entryEvidence()).isNull();
	}

	private DrawRunView run(UUID id, DrawType type, UUID firstId, Instant fixedAt) {
		return new DrawRunView(id, eventId, type == DrawType.INITIAL ? 0 : 2, type, DrawRunStatus.CONFIRMED,
				firstId, null, null, fixedAt, fixedAt, fixedAt, fixedAt);
	}
}
