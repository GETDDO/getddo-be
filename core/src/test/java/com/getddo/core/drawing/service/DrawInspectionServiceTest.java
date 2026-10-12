package com.getddo.core.drawing.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.*;
import com.getddo.core.user.domain.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.getddo.core.drawing.exception.DrawInspectionErrorCode.*;

class DrawInspectionServiceTest {
	private final DrawInspectionRepository repository = mock(DrawInspectionRepository.class);
	private final DrawCheckRepository checks = mock(DrawCheckRepository.class);
	private final Instant now = Instant.parse("2026-10-12T01:00:00Z");
	private final DrawInspectionService service = new DrawInspectionService(repository, checks,
			new TimeProvider(Clock.fixed(now, ZoneOffset.UTC)));
	private final UUID runId = UUID.randomUUID(), eventId = UUID.randomUUID();
	private final User admin = user(UserRole.ADMIN, UserStatus.ACTIVE);

	@Test
	@DisplayName("모든 관리자 업무는 일반·비활성 사용자 요청을 저장소 조회 전에 거절한다")
	void rejectsNonAdminsBeforeReading() {
		// given / when / then
		for (User user : List.of(user(UserRole.USER, UserStatus.ACTIVE), user(UserRole.ADMIN, UserStatus.INACTIVE))) {
			assertThatThrownBy(() -> service.runs(user, eventId, new PageQuery(1, 20))).isInstanceOf(BusinessException.class);
			assertThatThrownBy(() -> service.detail(user, runId)).isInstanceOf(BusinessException.class);
			assertThatThrownBy(() -> service.candidates(user, runId, new PageQuery(1, 20))).isInstanceOf(BusinessException.class);
			assertThatThrownBy(() -> service.verify(user, runId)).isInstanceOf(BusinessException.class);
			assertThatThrownBy(() -> service.history(user, runId, new PageQuery(1, 20))).isInstanceOf(BusinessException.class);
		}
		verifyNoInteractions(repository, checks);
	}

	@Test
	@DisplayName("없는 실행과 미확정 실행은 검증 이력을 만들지 않는다")
	void missingOrUnconfirmedRunCannotBeChecked() {
		// given / when / then
		when(repository.findRun(runId)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.verify(admin, runId)).isInstanceOf(BusinessException.class)
				.satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(RUN_NOT_FOUND));
		when(repository.findRun(runId)).thenReturn(Optional.of(run(DrawRunStatus.READY)));
		assertThatThrownBy(() -> service.verify(admin, runId)).isInstanceOf(BusinessException.class)
				.satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(CHECK_NOT_ALLOWED));
		verifyNoInteractions(checks);
	}

	@Test
	@DisplayName("저장 스냅샷 불일치는 검증자·시각·항목별 실패 결과로 저장한다")
	void persistsFailedCheckWithoutExecutingADraw() {
		// given
		when(repository.findRun(runId)).thenReturn(Optional.of(run(DrawRunStatus.CONFIRMED)));
		when(repository.findAllCandidates(runId)).thenReturn(List.of());
		when(repository.findResults(runId)).thenReturn(List.of());
		// when
		service.verify(admin, runId);
		// then
		verify(checks).save(eq(runId), eq(admin.id()), eq(now), argThat(items -> items.stream().anyMatch(item -> !item.isPassed())));
		verify(repository).findAllCandidates(runId);
		verify(repository).findResults(runId);
	}

	@Test
	@DisplayName("이벤트 소속과 페이지 조건을 보존하고 후보 조회는 실제 실행 ID를 사용한다")
	void preservesPageAndRunScope() {
		// given
		PageQuery query = new PageQuery(3, 2);
		when(repository.eventExists(eventId)).thenReturn(true);
		PageResult<DrawRunView> page = new PageResult<>(List.of(), 3, 2, 4);
		when(repository.findRuns(eventId, query)).thenReturn(page);
		when(repository.findRun(runId)).thenReturn(Optional.of(run(DrawRunStatus.CONFIRMED)));
		// when / then
		assertThat(service.runs(admin, eventId, query)).isSameAs(page);
		service.candidates(admin, runId, query);
		verify(repository).findCandidates(runId, query);
	}

	@Test
	@DisplayName("재추첨 검증은 같은 서비스 흐름에서 저장된 최초 입력·과거 배정을 읽고 이력을 저장한다")
	void replacementReadsReferencesAndPersistsCheck() {
		// given: 저장소 대역이며 실제 취소·실행 서비스 연동은 GD-169다.
		UUID firstId = UUID.randomUUID(), prizeId = UUID.randomUUID(), userId = UUID.randomUUID(), candidateId = UUID.randomUUID();
		DrawSnapshot.Rules firstRules = new DrawSnapshot.Rules(1, false, "NO_TICKET", "ALL", 1, 3, 5,
				List.of(new DrawSnapshotSource.Prize(prizeId, 1, 1)));
		DrawRunView first = new DrawRunView(firstId, eventId, 0, DrawType.INITIAL, DrawRunStatus.CONFIRMED,
				null, DrawSnapshotService.ALGORITHM_VERSION, firstRules, now.minusSeconds(10), now.minusSeconds(9), now.minusSeconds(9), now.minusSeconds(11));
		DrawSnapshot.Candidate winner = new DrawSnapshot.Candidate(candidateId, UUID.randomUUID(), userId, 0, 1,
				new DrawSnapshot.EntryEvidence(1, 0, 0, 0, List.of(new DrawSnapshotSource.Entry(UUID.randomUUID(), 0, now.minusSeconds(20), List.of()))),
				new DrawSnapshot.EligibilityEvidence(1, userId, "VIP", "USER", false, now.minusSeconds(10)));
		DrawRunView replacement = new DrawRunView(runId, eventId, 1, DrawType.REPLACEMENT, DrawRunStatus.CONFIRMED, firstId,
				DrawSnapshotService.ALGORITHM_VERSION, new DrawSnapshot.Rules(2, false, "NO_TICKET", "ALL", 1, 3, 5, firstRules.prizes()),
				now, now, now, now, new DrawReplacementRulesView(firstId, List.of(new DrawReplacementRulesView.Slot(prizeId, 1, 1)), List.of()));
		DrawVerificationReferences references = new DrawVerificationReferences(first,
				List.of(new DrawCandidateView(winner, firstId, eventId, eventId, userId)),
				List.of(new DrawHistoricalAward(UUID.randomUUID(), firstId, eventId, prizeId, eventId, 1, candidateId, userId, first.getConfirmedAt())));
		when(repository.findRun(runId)).thenReturn(Optional.of(replacement));
		when(repository.findReplacementReferences(replacement)).thenReturn(references);
		when(repository.findAllCandidates(runId)).thenReturn(List.of());
		when(repository.findResults(runId)).thenReturn(List.of(new DrawResultView(UUID.randomUUID(), runId, prizeId, eventId,
				1, null, DrawSelectionResultType.UNFILLED, null, false, false)));
		// when
		service.verify(admin, runId);
		// then
		verify(repository).findReplacementReferences(replacement);
		verify(checks).save(eq(runId), eq(admin.id()), eq(now), argThat(items -> items.size() == 7 && items.stream().allMatch(DrawCheckItem::isPassed)));
	}

	@Test
	@DisplayName("상세는 전체 후보를 읽지 않고 저장 등수·자리 순으로 사용자 ID를 제공한다")
	void detailReadsOnlyResultsAndUsesStoredPrizeRanks() {
		UUID firstPrize = UUID.randomUUID(), secondPrize = UUID.randomUUID(), winner = UUID.randomUUID();
		DrawSnapshot.Rules rules = new DrawSnapshot.Rules(1, false, "NO_TICKET", "ALL", 1, 3, 5,
				List.of(new DrawSnapshotSource.Prize(secondPrize, 2, 1), new DrawSnapshotSource.Prize(firstPrize, 1, 2)));
		DrawRunView run = new DrawRunView(runId, eventId, 0, DrawType.INITIAL, DrawRunStatus.CONFIRMED,
				null, DrawSnapshotService.ALGORITHM_VERSION, rules, now, now, now, now);
		DrawResultView second = new DrawResultView(UUID.randomUUID(), runId, secondPrize, eventId, 1, 3,
				DrawSelectionResultType.SELECTED, UUID.randomUUID(), winner, false, false);
		DrawResultView firstSlotTwo = new DrawResultView(UUID.randomUUID(), runId, firstPrize, eventId, 2, 2,
				DrawSelectionResultType.SELECTED, UUID.randomUUID(), winner, false, false);
		DrawResultView firstSlotOne = new DrawResultView(UUID.randomUUID(), runId, firstPrize, eventId, 1, 1,
				DrawSelectionResultType.SELECTED, UUID.randomUUID(), winner, false, false);
		when(repository.findRun(runId)).thenReturn(Optional.of(run));
		when(repository.findResults(runId)).thenReturn(List.of(second, firstSlotTwo, firstSlotOne));
		DrawRunDetailView detail = service.detail(admin, runId);
		assertThat(detail.getResults()).containsExactly(firstSlotOne, firstSlotTwo, second);
		assertThat(detail.getResults().getFirst().getUserId()).isEqualTo(winner);
		verify(repository, never()).findAllCandidates(any());
	}

	private DrawRunView run(DrawRunStatus status) {
		return new DrawRunView(runId, eventId, 0, DrawType.INITIAL, status, null, null, null, null, null, now, now);
	}
	private static User user(UserRole role, UserStatus status) {
		return new User(UUID.randomUUID(), "검증자", role, status, null, null, null, null, null, null, null);
	}
}
