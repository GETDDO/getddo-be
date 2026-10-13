package com.getddo.core.drawing.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.ticket.domain.TicketGrade;
import static org.assertj.core.api.Assertions.*;

class DrawStoredVerificationTest {
	private final UUID runId = UUID.randomUUID(), eventId = UUID.randomUUID(), prizeId = UUID.randomUUID();
	private final UUID candidateId = UUID.randomUUID(), participantId = UUID.randomUUID(), userId = UUID.randomUUID();
	private final Instant fixedAt = Instant.parse("2026-10-12T00:00:00Z");

	@Test
	@DisplayName("저장된 가중치·응모 근거·선정·미충원을 난수 없이 검증한다")
	void verifiesStoredResults() {
		// given
		DrawRunView run = run(DrawType.INITIAL);
		// when
		List<DrawCheckItem> checks = DrawStoredVerification.verify(run, List.of(candidate(1, 5)),
				List.of(result(1, 1, candidateId, true, true), result(2, null, null, false, false)));
		// then: 공개 후 취소된 당첨도 원래 확정 결과의 정합성을 바꾸지 않는다.
		assertThat(checks).allMatch(DrawCheckItem::isPassed);
	}

	@Test
	@DisplayName("실제 장수와 가중치를 구분하고 변조된 수량·가중치를 거절한다")
	void rejectsWrongWeightAndCount() {
		// given / when / then
		assertThat(checks(List.of(candidate(5, 5)), validResults())).anyMatch(item -> item.getCode().equals("CANDIDATE_EVIDENCE") && !item.isPassed());
		assertThat(checks(List.of(candidate(1, 1)), validResults())).anyMatch(item -> item.getCode().equals("CANDIDATE_EVIDENCE") && !item.isPassed());
	}

	@ParameterizedTest
	@EnumSource(TicketGrade.class)
	@DisplayName("가중치 미적용 응모권 이벤트는 저장된 브론즈 한 장 근거만 통과한다")
	void unweightedTicketEvidenceRequiresBronze(TicketGrade grade) {
		// given
		DrawRunView weighted = run(DrawType.INITIAL);
		DrawRunView unweighted = new DrawRunView(runId, eventId, 0, DrawType.INITIAL, DrawRunStatus.CONFIRMED, null,
				weighted.getAlgorithmVersion(), new DrawSnapshot.Rules(1, false, "TICKET", "ALL", 1, 3, 5,
				weighted.getRules().prizes()), fixedAt, fixedAt, fixedAt, fixedAt);
		DrawSnapshotSource.Use use = new DrawSnapshotSource.Use(UUID.randomUUID(), UUID.randomUUID(), userId, grade);
		DrawSnapshotSource.Entry entry = new DrawSnapshotSource.Entry(UUID.randomUUID(), 1, fixedAt.minusSeconds(300), List.of(use));
		DrawSnapshot.Candidate snapshot = new DrawSnapshot.Candidate(candidateId, participantId, userId, 1, 1,
				new DrawSnapshot.EntryEvidence(1, grade == TicketGrade.BRONZE ? 1 : 0, grade == TicketGrade.SILVER ? 1 : 0,
				grade == TicketGrade.GOLD ? 1 : 0, List.of(entry)),
				new DrawSnapshot.EligibilityEvidence(1, userId, "VIP", "USER", false, fixedAt));
		// when
		List<DrawCheckItem> checks = DrawStoredVerification.verify(unweighted,
				List.of(new DrawCandidateView(snapshot, runId, eventId, eventId, userId)), validResults());
		// then
		assertThat(checks).filteredOn(item -> item.getCode().equals("CANDIDATE_EVIDENCE"))
				.extracting(DrawCheckItem::isPassed).containsExactly(grade == TicketGrade.BRONZE);
	}

	@Test
	@DisplayName("후보 부족 이전 미충원·중복 자리·잘못된 실행 연결·선정 순서를 거절한다")
	void rejectsInvalidResults() {
		// given / when / then
		assertResultsFail(List.of(result(1, null, null, false, false), result(2, 1, candidateId, false, false)));
		assertResultsFail(List.of(result(1, 1, candidateId, false, false), result(1, null, null, false, false)));
		assertResultsFail(List.of(result(1, 2, candidateId, false, false), result(2, null, null, false, false)));
		assertResultsFail(List.of(new DrawResultView(UUID.randomUUID(), UUID.randomUUID(), prizeId, eventId, 1, 1,
				DrawSelectionResultType.SELECTED, candidateId, false, false), result(2, null, null, false, false)));
	}

	@Test
	@DisplayName("중복 사용자 후보·다른 이벤트 후보 연결을 불일치로 기록한다")
	void rejectsCandidateRelations() {
		// given
		DrawCandidateView candidate = candidate(1, 5);
		DrawCandidateView wrongEvent = new DrawCandidateView(candidate.getSnapshot(), runId, UUID.randomUUID(), eventId, userId);
		// when / then
		assertThat(checks(List.of(candidate, candidate), validResults())).anyMatch(item -> item.getCode().equals("CANDIDATE_RELATIONS") && !item.isPassed());
		assertThat(checks(List.of(wrongEvent), validResults())).anyMatch(item -> item.getCode().equals("CANDIDATE_RELATIONS") && !item.isPassed());
	}

	@Test
	@DisplayName("버전 1 입력의 재추첨을 버전 2 검증으로 통과시키지 않는다")
	void replacementWithVersionOneIsUnsupported() {
		// given / when / then
		assertThat(DrawStoredVerification.verify(run(DrawType.REPLACEMENT), List.of(candidate(1, 5)), validResults()))
				.anyMatch(item -> item.getCode().equals("SUPPORTED_INPUT") && !item.isPassed());
	}

	@Test
	@DisplayName("손상된 JSON 복원값과 누락 시각은 예외 원문 대신 불일치가 된다")
	void missingEvidenceAndTimeFailSafely() {
		// given
		DrawSnapshot.Candidate broken = new DrawSnapshot.Candidate(candidateId, participantId, null, 1, 5, null, null);
		DrawRunView run = new DrawRunView(runId, eventId, 0, DrawType.INITIAL, DrawRunStatus.CONFIRMED,
				null, DrawSnapshotService.ALGORITHM_VERSION, run(DrawType.INITIAL).getRules(), null, null, fixedAt, fixedAt);
		// when / then
		assertThat(DrawStoredVerification.verify(run, List.of(new DrawCandidateView(broken, runId, eventId, eventId, userId)), validResults()))
				.anyMatch(item -> !item.isPassed());
	}

	private List<DrawCheckItem> checks(List<DrawCandidateView> candidates, List<DrawResultView> results) {
		return DrawStoredVerification.verify(run(DrawType.INITIAL), candidates, results);
	}
	private void assertResultsFail(List<DrawResultView> results) {
		assertThat(checks(List.of(candidate(1, 5)), results)).anyMatch(item -> item.getCode().equals("RESULTS") && !item.isPassed());
	}
	private List<DrawResultView> validResults() {
		return List.of(result(1, 1, candidateId, false, false), result(2, null, null, false, false));
	}
	private DrawResultView result(int slot, Integer order, UUID candidate, boolean published, boolean canceled) {
		return new DrawResultView(UUID.randomUUID(), runId, prizeId, eventId, slot, order,
				candidate == null ? DrawSelectionResultType.UNFILLED : DrawSelectionResultType.SELECTED, candidate, published, canceled);
	}
	private DrawRunView run(DrawType type) {
		return new DrawRunView(runId, eventId, 0, type, DrawRunStatus.CONFIRMED, null,
				DrawSnapshotService.ALGORITHM_VERSION, new DrawSnapshot.Rules(1, true, "TICKET", "ALL", 1, 3, 5,
				List.of(new DrawSnapshotSource.Prize(prizeId, 1, 2))), fixedAt, fixedAt, fixedAt, fixedAt);
	}
	private DrawCandidateView candidate(long count, long weight) {
		DrawSnapshotSource.Use use = new DrawSnapshotSource.Use(UUID.randomUUID(), UUID.randomUUID(), userId, TicketGrade.GOLD);
		DrawSnapshotSource.Entry entry = new DrawSnapshotSource.Entry(UUID.randomUUID(), 1, fixedAt.minusSeconds(300), List.of(use));
		DrawSnapshot.Candidate snapshot = new DrawSnapshot.Candidate(candidateId, participantId, userId, count, weight,
				new DrawSnapshot.EntryEvidence(1, 0, 0, 1, List.of(entry)),
				new DrawSnapshot.EligibilityEvidence(1, userId, "VIP", "USER", false, fixedAt));
		return new DrawCandidateView(snapshot, runId, eventId, eventId, userId);
	}
}
