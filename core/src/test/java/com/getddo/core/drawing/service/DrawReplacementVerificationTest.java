package com.getddo.core.drawing.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.getddo.core.drawing.domain.*;
import static org.assertj.core.api.Assertions.*;

/** 저장 입력·원본·이전 결과를 구성하는 단위 테스트다. 실제 취소·실행 서비스 연동이 아니다. */
class DrawReplacementVerificationTest {
	private final UUID eventId = UUID.randomUUID(), prizeId = UUID.randomUUID(), originalId = UUID.randomUUID(), runId = UUID.randomUUID();
	private final Instant fixedAt = Instant.parse("2026-10-12T00:00:00Z");

	@Test
	@DisplayName("유지 배정을 보존하고 실제 빈 자리 3에 저장된 재추첨 결과를 검증한다")
	void validatesActualSlotAndRetainedAssignments() {
		// given
		Fixture fixture = fixture(2, List.of(3));
		// when / then
		assertThat(check(fixture, fixture.run(), fixture.candidates(), fixture.results())).allMatch(DrawCheckItem::isPassed);
	}

	@Test
	@DisplayName("후보 부족은 실제 대상 자리의 선정·미충원으로 검증한다")
	void validatesShortage() {
		// given
		Fixture fixture = fixture(1, List.of(2, 3));
		// when / then
		assertThat(fixture.results()).extracting(DrawResultView::getResultType).containsExactly(DrawSelectionResultType.SELECTED, DrawSelectionResultType.UNFILLED);
		assertThat(check(fixture, fixture.run(), fixture.candidates(), fixture.results())).allMatch(DrawCheckItem::isPassed);
	}

	@Test
	@DisplayName("무후보 재추첨도 대상 자리만 UNFILLED이면 정상 검증된다")
	void validatesNoCandidates() {
		// given
		Fixture fixture = fixture(0, List.of(2, 3));
		// when / then
		assertThat(check(fixture, fixture.run(), fixture.candidates(), fixture.results())).allMatch(DrawCheckItem::isPassed);
	}

	@Test
	@DisplayName("대상 밖 자리·잘못된 순서·후보가 남은 미충원·중복 선정은 불일치다")
	void rejectsInvalidResults() {
		// given
		Fixture fixture = fixture(2, List.of(2, 3));
		UUID first = fixture.candidates().getFirst().getSnapshot().id();
		UUID second = fixture.candidates().getLast().getSnapshot().id();
		// when / then
		assertFailed(check(fixture, fixture.run(), fixture.candidates(), List.of(result(1, 1, first), result(3, 2, second))), "RESULTS");
		assertFailed(check(fixture, fixture.run(), fixture.candidates(), List.of(result(2, 2, first), result(3, 1, second))), "RESULTS");
		assertFailed(check(fixture, fixture.run(), fixture.candidates(), List.of(result(2, 1, first), result(3, null, null))), "RESULTS");
		assertFailed(check(fixture, fixture.run(), fixture.candidates(), List.of(result(2, 1, first), result(3, 2, first))), "RESULTS");
	}

	@Test
	@DisplayName("최초 후보의 누락·기존 당첨자 재포함·스냅샷 변경은 불일치다")
	void rejectsChangedOrMissingOriginalCandidates() {
		// given
		Fixture fixture = fixture(2, List.of(3));
		List<DrawCandidateView> includedWinner = new ArrayList<>(fixture.candidates());
		includedWinner.add(fixture.references().getOriginalCandidates().getFirst());
		DrawSnapshot.Candidate original = fixture.candidates().getFirst().getSnapshot();
		DrawSnapshot.Candidate changed = new DrawSnapshot.Candidate(original.id(), original.participantId(), original.userId(), 0, 2,
				original.entryEvidence(), original.eligibilityEvidence());
		// when / then
		assertFailed(check(fixture, fixture.run(), fixture.candidates().subList(0, 1), fixture.results()), "REPLACEMENT_SOURCE");
		assertFailed(check(fixture, fixture.run(), includedWinner, fixture.results()), "REPLACEMENT_SOURCE");
		assertFailed(check(fixture, fixture.run(), List.of(new DrawCandidateView(changed, originalId, eventId, eventId, original.userId()), fixture.candidates().getLast()),
				fixture.results()), "REPLACEMENT_SOURCE");
	}

	@Test
	@DisplayName("최초 규칙·이벤트·원본 실행 연결이 다르면 검증을 통과하지 않는다")
	void rejectsDifferentOriginalRulesAndLinks() {
		// given
		Fixture fixture = fixture(1, List.of(3));
		DrawRunView wrongId = replacement(fixture.run().getRules(), new DrawReplacementRulesView(UUID.randomUUID(),
				fixture.run().getReplacementRules().getTargetSlots(), fixture.run().getReplacementRules().getRetainedAwards()));
		DrawSnapshot.Rules changed = new DrawSnapshot.Rules(2, false, "NO_TICKET", "different", 1, 3, 5, fixture.run().getRules().prizes(), originalId, fixture.run().getRules().targetSlots(), fixture.run().getRules().retainedAwards());
		// when / then
		assertFailed(check(fixture, wrongId, fixture.candidates(), fixture.results()), "REPLACEMENT_SOURCE");
		assertFailed(check(fixture, replacement(changed, fixture.run().getReplacementRules()), fixture.candidates(), fixture.results()), "REPLACEMENT_SOURCE");
		DrawRunView first = fixture.references().getOriginalRun();
		DrawRunView foreign = new DrawRunView(originalId, UUID.randomUUID(), 0, DrawType.INITIAL, DrawRunStatus.CONFIRMED,
				null, first.getAlgorithmVersion(), first.getRules(), first.getSnapshotFixedAt(), first.getStartedAt(), first.getConfirmedAt(), first.getCreatedAt());
		assertFailed(DrawStoredVerification.verify(fixture.run(), fixture.candidates(), fixture.results(),
				new DrawVerificationReferences(foreign, fixture.references().getOriginalCandidates(), fixture.references().getHistoricalAwards())), "REPLACEMENT_SOURCE");
	}

	@Test
	@DisplayName("유지 결과 ID·사용자·배정 변경과 대상 자리 겹침을 거절한다")
	void rejectsChangedRetainedAssignment() {
		// given
		Fixture fixture = fixture(1, List.of(3));
		DrawReplacementRulesView rules = fixture.run().getReplacementRules();
		DrawReplacementRulesView.RetainedAward retained = rules.getRetainedAwards().getFirst();
		List<DrawReplacementRulesView.RetainedAward> changed = List.of(new DrawReplacementRulesView.RetainedAward(retained.getResultId(),
				UUID.randomUUID(), retained.getPrizeId(), retained.getSlotNumber()), rules.getRetainedAwards().getLast());
		// when / then
		assertFailed(check(fixture, replacement(fixture.run().getRules(), new DrawReplacementRulesView(originalId, rules.getTargetSlots(), changed)),
				fixture.candidates(), fixture.results()), "REPLACEMENT_SLOTS");
		assertFailed(check(fixture, replacement(fixture.run().getRules(), new DrawReplacementRulesView(originalId,
				List.of(new DrawReplacementRulesView.Slot(prizeId, 1, 1)), rules.getRetainedAwards())), fixture.candidates(), fixture.results()), "REPLACEMENT_SLOTS");
	}

	@Test
	@DisplayName("중복·범위 밖·등수 불일치 대상 자리와 존재하지 않는 유지 결과를 거절한다")
	void rejectsInvalidTargetShape() {
		// given
		Fixture fixture = fixture(1, List.of(3));
		DrawReplacementRulesView rules = fixture.run().getReplacementRules();
		// when / then
		for (List<DrawReplacementRulesView.Slot> slots : List.of(
				List.of(new DrawReplacementRulesView.Slot(prizeId, 1, 3), new DrawReplacementRulesView.Slot(prizeId, 1, 3)),
				List.of(new DrawReplacementRulesView.Slot(prizeId, 1, 4)), List.of(new DrawReplacementRulesView.Slot(prizeId, 2, 3)))) {
			assertFailed(check(fixture, replacement(fixture.run().getRules(), new DrawReplacementRulesView(originalId, slots, rules.getRetainedAwards())),
					fixture.candidates(), fixture.results()), "REPLACEMENT_SLOTS");
		}
		assertFailed(check(fixture, replacement(fixture.run().getRules(), new DrawReplacementRulesView(originalId, rules.getTargetSlots(),
				List.of(new DrawReplacementRulesView.RetainedAward(UUID.randomUUID(), UUID.randomUUID(), prizeId, 1)))), fixture.candidates(), fixture.results()), "REPLACEMENT_SLOTS");
	}

	@Test
	@DisplayName("최초 가중치를 보존하고 과거 재추첨에서 당첨된 후보도 재포함하지 않는다")
	void excludesHistoricalReplacementWinner() {
		// given
		Fixture fixture = fixture(2, List.of(3));
		DrawSnapshot.Candidate previouslyWon = fixture.candidates().getFirst().getSnapshot();
		List<DrawHistoricalAward> awards = new ArrayList<>(fixture.references().getHistoricalAwards());
		awards.add(new DrawHistoricalAward(UUID.randomUUID(), UUID.randomUUID(), eventId, prizeId, eventId, 3,
				previouslyWon.id(), previouslyWon.userId(), fixedAt.minusSeconds(30), 1, fixedAt.minusSeconds(1), DrawSelectionResultType.SELECTED));
		DrawVerificationReferences references = new DrawVerificationReferences(fixture.references().getOriginalRun(), fixture.references().getOriginalCandidates(), awards);
		// when / then
		assertFailed(DrawStoredVerification.verify(fixture.run(), fixture.candidates(), fixture.results(), references), "REPLACEMENT_SOURCE");
		List<DrawCandidateView> remaining = List.of(fixture.candidates().getLast());
		assertThat(DrawStoredVerification.verify(fixture.run(), remaining, List.of(result(3, 1, remaining.getFirst().getSnapshot().id())), references))
				.allMatch(DrawCheckItem::isPassed);
	}

	@Test
	@DisplayName("새 수량과 가중치가 자체 일치해도 최초 골드 가중치를 바꾸면 검증 실패다")
	void rejectsSelfConsistentWeightRewrite() {
		// given
		Fixture fixture = fixture(1, List.of(3));
		List<DrawCandidateView> weighted = fixture.references().getOriginalCandidates().stream().map(view -> {
			DrawSnapshot.Candidate old = view.getSnapshot();
			DrawSnapshotSource.Use use = new DrawSnapshotSource.Use(UUID.randomUUID(), UUID.randomUUID(), old.userId(), com.getddo.core.ticket.domain.TicketGrade.GOLD);
			DrawSnapshot.Candidate snapshot = new DrawSnapshot.Candidate(old.id(), old.participantId(), old.userId(), 1, 5,
					new DrawSnapshot.EntryEvidence(1, 0, 0, 1, List.of(new DrawSnapshotSource.Entry(UUID.randomUUID(), 1, fixedAt.minusSeconds(700), List.of(use)))), old.eligibilityEvidence());
			return new DrawCandidateView(snapshot, originalId, eventId, eventId, old.userId());
		}).toList();
		DrawRunView first = fixture.references().getOriginalRun();
		DrawRunView original = new DrawRunView(originalId, eventId, 0, DrawType.INITIAL, DrawRunStatus.CONFIRMED, null,
				first.getAlgorithmVersion(), new DrawSnapshot.Rules(1, true, "TICKET", "ALL", 1, 3, 5, first.getRules().prizes()),
				first.getSnapshotFixedAt(), first.getStartedAt(), first.getConfirmedAt(), first.getCreatedAt());
		DrawRunView run = replacement(new DrawSnapshot.Rules(2, true, "TICKET", "ALL", 1, 3, 5, first.getRules().prizes(), originalId, fixture.run().getRules().targetSlots(), fixture.run().getRules().retainedAwards()), fixture.run().getReplacementRules());
		DrawVerificationReferences references = new DrawVerificationReferences(original, weighted, fixture.references().getHistoricalAwards());
		DrawCandidateView candidate = weighted.getLast();
		assertThat(DrawStoredVerification.verify(run, List.of(candidate), fixture.results(), references)).allMatch(DrawCheckItem::isPassed);
		DrawSnapshot.Candidate old = candidate.getSnapshot();
		List<DrawSnapshotSource.Use> uses = List.of(
				new DrawSnapshotSource.Use(UUID.randomUUID(), UUID.randomUUID(), old.userId(), com.getddo.core.ticket.domain.TicketGrade.BRONZE),
				new DrawSnapshotSource.Use(UUID.randomUUID(), UUID.randomUUID(), old.userId(), com.getddo.core.ticket.domain.TicketGrade.BRONZE));
		DrawSnapshot.Candidate rewritten = new DrawSnapshot.Candidate(old.id(), old.participantId(), old.userId(), 2, 2,
				new DrawSnapshot.EntryEvidence(1, 2, 0, 0, List.of(new DrawSnapshotSource.Entry(UUID.randomUUID(), 2, fixedAt.minusSeconds(700), uses))), old.eligibilityEvidence());
		// when
		List<DrawCheckItem> checks = DrawStoredVerification.verify(run, List.of(new DrawCandidateView(rewritten, originalId, eventId, eventId, old.userId())), fixture.results(), references);
		// then
		assertThat(checks).filteredOn(item -> item.getCode().equals("CANDIDATE_EVIDENCE")).allMatch(DrawCheckItem::isPassed);
		assertFailed(checks, "REPLACEMENT_SOURCE");
	}

	@Test
	@DisplayName("이후 공개·취소 여부가 바뀌어도 저장된 같은 실행 검증은 바뀌지 않는다")
	void ignoresLaterCancellationAndPublication() {
		// given
		Fixture fixture = fixture(1, List.of(3));
		DrawResultView result = fixture.results().getFirst();
		DrawResultView laterCanceled = new DrawResultView(result.getId(), result.getRunId(), result.getPrizeId(), result.getPrizeEventId(),
				result.getSlotNumber(), result.getSelectionOrder(), result.getResultType(), result.getCandidateId(), true, true);
		// when / then
		assertThat(check(fixture, fixture.run(), fixture.candidates(), List.of(laterCanceled))).allMatch(DrawCheckItem::isPassed);
		assertThat(check(fixture, fixture.run(), fixture.candidates(), List.of(laterCanceled))).usingRecursiveComparison()
				.isEqualTo(check(fixture, fixture.run(), fixture.candidates(), fixture.results()));
	}

	@Test
	@DisplayName("최초 입력 근거를 조회하지 못하면 재추첨을 통과로 기록하지 않는다")
	void missingReferencesFail() {
		// given
		Fixture fixture = fixture(1, List.of(3));
		// when / then
		assertFailed(DrawStoredVerification.verify(fixture.run(), fixture.candidates(), fixture.results()), "REPLACEMENT_SOURCE");
	}

	@Test @DisplayName("취소되지 않은 자리 선택·유지 당첨 누락·취소된 당첨 유지를 거절한다")
	void rejectsIncompleteOrUncancelledAssignments() {
		// given
		Fixture fixture = fixture(1, List.of(3));
		DrawReplacementRulesView rules = fixture.run().getReplacementRules();
		List<DrawHistoricalAward> uncanceled = fixture.references().getHistoricalAwards().stream()
				.map(a -> history(a, null)).toList();
		// when / then
		assertFailed(DrawStoredVerification.verify(fixture.run(), fixture.candidates(), fixture.results(),
				new DrawVerificationReferences(fixture.references().getOriginalRun(), fixture.references().getOriginalCandidates(), uncanceled)), "REPLACEMENT_SLOTS");
		DrawRunView missing = replacement(fixture.run().getRules(), new DrawReplacementRulesView(originalId,
				rules.getTargetSlots(), rules.getRetainedAwards().subList(0, 1)));
		assertFailed(check(fixture, missing, fixture.candidates(), fixture.results()), "REPLACEMENT_SLOTS");
		List<DrawHistoricalAward> canceledRetained = fixture.references().getHistoricalAwards().stream()
				.map(a -> history(a, fixedAt.minusSeconds(1))).toList();
		assertFailed(DrawStoredVerification.verify(fixture.run(), fixture.candidates(), fixture.results(),
				new DrawVerificationReferences(fixture.references().getOriginalRun(), fixture.references().getOriginalCandidates(), canceledRetained)), "REPLACEMENT_SLOTS");
	}

	@Test @DisplayName("고정 이후의 취소는 유지 배정을 소급 변경하지 않는다")
	void usesCancellationTimeInsteadOfCurrentStatus() {
		// given
		Fixture fixture = fixture(1, List.of(3));
		List<DrawHistoricalAward> history = fixture.references().getHistoricalAwards().stream()
				.map(a -> a.getSlotNumber() == 3 ? a : history(a, fixedAt.plusSeconds(1))).toList();
		// when / then
		assertThat(DrawStoredVerification.verify(fixture.run(), fixture.candidates(), fixture.results(),
				new DrawVerificationReferences(fixture.references().getOriginalRun(), fixture.references().getOriginalCandidates(), history)))
				.allMatch(DrawCheckItem::isPassed);
	}

	private DrawHistoricalAward history(DrawHistoricalAward a, Instant canceledAt) {
		return new DrawHistoricalAward(a.getId(), a.getRunId(), a.getEventId(), a.getPrizeId(), a.getPrizeEventId(),
				a.getSlotNumber(), a.getCandidateId(), a.getUserId(), a.getConfirmedAt(), a.getRunNumber(), canceledAt, a.getResultType());
	}

	private List<DrawCheckItem> check(Fixture fixture, DrawRunView run, List<DrawCandidateView> candidates, List<DrawResultView> results) {
		return DrawStoredVerification.verify(run, candidates, results, fixture.references());
	}
	private static void assertFailed(List<DrawCheckItem> items, String code) {
		assertThat(items).anyMatch(item -> item.getCode().equals(code) && !item.isPassed());
	}
	private Fixture fixture(int remainingCount, List<Integer> targets) {
		DrawSnapshot.Rules firstRules = new DrawSnapshot.Rules(1, false, "NO_TICKET", "ALL", 1, 3, 5,
				List.of(new DrawSnapshotSource.Prize(prizeId, 1, 3)));
		DrawRunView first = new DrawRunView(originalId, eventId, 0, DrawType.INITIAL, DrawRunStatus.CONFIRMED, null,
				DrawSnapshotService.ALGORITHM_VERSION, firstRules, fixedAt.minusSeconds(600), fixedAt.minusSeconds(599), fixedAt.minusSeconds(599), fixedAt.minusSeconds(601));
		List<DrawCandidateView> originals = new ArrayList<>();
		for (int index = 0; index < 3 + remainingCount; index++) originals.add(candidate());
		List<DrawHistoricalAward> history = new ArrayList<>();
		for (int slot = 1; slot <= 3; slot++) {
			DrawSnapshot.Candidate candidate = originals.get(slot - 1).getSnapshot();
			history.add(new DrawHistoricalAward(UUID.randomUUID(), originalId, eventId, prizeId, eventId, slot,
					candidate.id(), candidate.userId(), first.getConfirmedAt(), 0,
					targets.contains(slot) ? fixedAt.minusSeconds(1) : null, DrawSelectionResultType.SELECTED));
		}
		DrawReplacementRulesView metadata = new DrawReplacementRulesView(originalId,
				targets.stream().map(slot -> new DrawReplacementRulesView.Slot(prizeId, 1, slot)).toList(),
				history.stream().filter(award -> !targets.contains(award.getSlotNumber()))
						.map(award -> new DrawReplacementRulesView.RetainedAward(award.getId(), award.getUserId(), award.getPrizeId(), award.getSlotNumber())).toList());
		DrawSnapshot.Rules rules = new DrawSnapshot.Rules(2, false, "NO_TICKET", "ALL", 1, 3, 5, firstRules.prizes(), originalId,
				metadata.getTargetSlots().stream().map(slot -> new DrawSnapshot.Slot(slot.getPrizeId(), slot.getRank(), slot.getSlotNumber())).toList(),
				metadata.getRetainedAwards().stream().map(award -> new DrawSnapshot.RetainedAward(award.getResultId(), award.getUserId(), award.getPrizeId(), award.getSlotNumber())).toList());
		List<DrawCandidateView> remaining = originals.subList(3, originals.size());
		List<DrawResultView> results = new ArrayList<>();
		for (int index = 0; index < targets.size(); index++) {
			results.add(index < remaining.size() ? result(targets.get(index), index + 1, remaining.get(index).getSnapshot().id())
					: result(targets.get(index), null, null));
		}
		return new Fixture(replacement(rules, metadata), List.copyOf(remaining), List.copyOf(results), new DrawVerificationReferences(first, originals, history));
	}
	private DrawCandidateView candidate() {
		UUID user = UUID.randomUUID();
		DrawSnapshot.Candidate snapshot = new DrawSnapshot.Candidate(UUID.randomUUID(), UUID.randomUUID(), user, 0, 1,
				new DrawSnapshot.EntryEvidence(1, 0, 0, 0, List.of(new DrawSnapshotSource.Entry(UUID.randomUUID(), 0, fixedAt.minusSeconds(700), List.of()))),
				new DrawSnapshot.EligibilityEvidence(1, user, "VIP", "USER", false, fixedAt.minusSeconds(600)));
		return new DrawCandidateView(snapshot, originalId, eventId, eventId, user);
	}
	private DrawRunView replacement(DrawSnapshot.Rules rules, DrawReplacementRulesView metadata) {
		return new DrawRunView(runId, eventId, 2, DrawType.REPLACEMENT, DrawRunStatus.CONFIRMED, originalId,
				DrawSnapshotService.ALGORITHM_VERSION, rules, fixedAt, fixedAt, fixedAt, fixedAt, metadata);
	}
	private DrawResultView result(int slot, Integer order, UUID candidate) {
		return new DrawResultView(UUID.randomUUID(), runId, prizeId, eventId, slot, order,
				candidate == null ? DrawSelectionResultType.UNFILLED : DrawSelectionResultType.SELECTED, candidate, false, false);
	}
	private record Fixture(DrawRunView run, List<DrawCandidateView> candidates, List<DrawResultView> results, DrawVerificationReferences references) { }
}
