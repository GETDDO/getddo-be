package com.getddo.core.mission.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionDetailView;
import com.getddo.core.mission.domain.MissionSearch;
import com.getddo.core.mission.domain.MissionStatus;
import com.getddo.core.mission.domain.MissionSubmission;
import com.getddo.core.mission.domain.MissionSummary;
import com.getddo.core.mission.domain.MissionType;
import com.getddo.core.mission.domain.QuizOption;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.domain.QuizQuestionType;
import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;
import com.getddo.core.mission.repository.MissionRepository;
import com.getddo.core.mission.repository.MissionSubmissionRepository;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 미션 목록·상세 조회가 저장소 결과에 사용자의 완료 표시를 붙이는지 검증한다. */
class MissionQueryServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final Instant STARTS_AT = Instant.parse("2026-10-01T00:00:00Z");

	private final MissionRepository missionRepository = mock(MissionRepository.class);
	private final MissionSubmissionRepository submissionRepository = mock(MissionSubmissionRepository.class);
	private final MissionQueryService service = new MissionQueryService(missionRepository, submissionRepository);

	private static Mission mission(UUID id, MissionType type) {
		return new Mission(id, UUID.randomUUID(), UUID.randomUUID(), "미션", "설명", type, STARTS_AT,
				STARTS_AT.plusSeconds(86_400), MissionStatus.ACTIVE, null, STARTS_AT, STARTS_AT);
	}

	private static MissionDetail quizDetail(UUID missionId) {
		QuizQuestion question = new QuizQuestion(UUID.randomUUID(), QuizQuestionType.SINGLE_CHOICE, "문제", 1, null,
				List.of(new QuizOption(UUID.randomUUID(), "보기", 1, true)));
		return new MissionDetail(mission(missionId, MissionType.QUIZ), List.of(question), List.of());
	}

	@Test
	@DisplayName("목록은 페이지의 미션만 대상으로 완료한 미션을 한 번에 조회해 완료 표시를 붙이고 페이지 정보를 보존한다")
	void marksCompletedMissionsWithOneLookup() {
		// given
		UUID done = UUID.randomUUID();
		UUID open = UUID.randomUUID();
		PageQuery page = new PageQuery(2, 10);
		when(missionRepository.search(any(), any())).thenReturn(new PageResult<>(
				List.of(mission(done, MissionType.QUIZ), mission(open, MissionType.SURVEY)), 2, 10, 12));
		when(submissionRepository.findCompletedMissionIds(USER_ID, List.of(done, open))).thenReturn(Set.of(done));
		// when
		PageResult<MissionSummary> result = service.findMissions(USER_ID, null, page);
		// then
		assertThat(result.getItems()).extracting(item -> item.getMission().getId()).containsExactly(done, open);
		assertThat(result.getItems()).extracting(MissionSummary::isCompleted).containsExactly(true, false);
		assertThat(result.getPage()).isEqualTo(2);
		assertThat(result.getSize()).isEqualTo(10);
		assertThat(result.getTotalElements()).isEqualTo(12);
		verify(submissionRepository).findCompletedMissionIds(USER_ID, List.of(done, open));
	}

	@Test
	@DisplayName("유형 조건은 상태·키워드 없이 그대로 저장소 검색에 전달한다")
	void passesOnlyMissionTypeToSearch() {
		// given
		when(missionRepository.search(any(), any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when
		service.findMissions(USER_ID, MissionType.QUIZ, new PageQuery(1, 20));
		// then
		ArgumentCaptor<MissionSearch> search = ArgumentCaptor.forClass(MissionSearch.class);
		verify(missionRepository).search(search.capture(), any());
		assertThat(search.getValue().getMissionType()).isEqualTo(MissionType.QUIZ);
		assertThat(search.getValue().getStatus()).isNull();
		assertThat(search.getValue().getKeyword()).isNull();
	}

	@Test
	@DisplayName("목록이 비어 있으면 완료 여부를 조회하지 않는다")
	void skipsCompletionLookupForEmptyPage() {
		// given
		when(missionRepository.search(any(), any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when
		PageResult<MissionSummary> result = service.findMissions(USER_ID, null, new PageQuery(1, 20));
		// then
		assertThat(result.getItems()).isEmpty();
		verify(submissionRepository, never()).findCompletedMissionIds(any(), any());
	}

	@Test
	@DisplayName("상세는 사용자의 완료 제출이 있으면 완료로, 없으면 미완료로 표시한다")
	void marksDetailCompletedByCompletedSubmission() {
		// given
		UUID doneId = UUID.randomUUID();
		UUID openId = UUID.randomUUID();
		when(missionRepository.findDetailById(doneId)).thenReturn(Optional.of(quizDetail(doneId)));
		when(missionRepository.findDetailById(openId)).thenReturn(Optional.of(quizDetail(openId)));
		when(submissionRepository.findCompleted(USER_ID, doneId)).thenReturn(Optional.of(mock(MissionSubmission.class)));
		when(submissionRepository.findCompleted(USER_ID, openId)).thenReturn(Optional.empty());
		// when
		MissionDetailView done = service.findMission(USER_ID, doneId);
		MissionDetailView open = service.findMission(USER_ID, openId);
		// then
		assertThat(done.isCompleted()).isTrue();
		assertThat(done.getDetail().getMission().getId()).isEqualTo(doneId);
		assertThat(open.isCompleted()).isFalse();
	}

	@Test
	@DisplayName("없는 미션의 상세 조회는 MISSION-003이고 완료 여부를 조회하지 않는다")
	void throwsWhenMissionIsMissing() {
		// given
		UUID missingId = UUID.randomUUID();
		when(missionRepository.findDetailById(missingId)).thenReturn(Optional.empty());
		// when
		// then
		assertThatThrownBy(() -> service.findMission(USER_ID, missingId))
				.isInstanceOfSatisfying(MissionException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(MissionErrorCode.MISSION_NOT_FOUND));
		verify(submissionRepository, never()).findCompleted(any(), any());
	}
}
