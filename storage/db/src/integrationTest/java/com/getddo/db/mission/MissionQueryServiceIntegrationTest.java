package com.getddo.db.mission;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionAnswerInput;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionDetailView;
import com.getddo.core.mission.domain.MissionSubmissionCommand;
import com.getddo.core.mission.domain.MissionSummary;
import com.getddo.core.mission.domain.MissionType;
import com.getddo.core.mission.domain.QuizOption;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.domain.SurveyOption;
import com.getddo.core.mission.domain.SurveyQuestion;
import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;
import com.getddo.core.mission.service.MissionQueryService;
import com.getddo.core.mission.service.MissionSubmissionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 미션 목록·상세 조회가 실제 MySQL의 미션·제출 행으로 완료 표시와 정렬을 맞게 만드는지 검증한다. */
class MissionQueryServiceIntegrationTest extends MissionIntegrationTestSupport {

	private static final PageQuery ALL = new PageQuery(1, 100);

	@Autowired
	private MissionQueryService queryService;
	@Autowired
	private MissionSubmissionService submissionService;

	private MissionDetail saveSurveyStartingAt(String title, Instant startsAt) {
		Mission mission = Mission.register(rewardPolicyId, adminId, title, "설명", MissionType.SURVEY, startsAt,
				startsAt.plusSeconds(86_400L * 30), null);
		MissionDetail template = saveSurveyMission(title + "-template");
		MissionDetail detail = new MissionDetail(mission, List.of(), template.getSurveyQuestions().stream()
				.map(question -> SurveyQuestion.of(question.getQuestionType(),
						question.getQuestionText(), question.isRequired(), question.getDisplayOrder(),
						question.getOptions().stream().map(option -> SurveyOption.of(
								option.getOptionText(), option.getDisplayOrder())).toList()))
				.toList());
		return transaction.execute(status -> missionRepository.insert(detail));
	}

	private void completeQuiz(UUID user, MissionDetail quiz) {
		List<QuizQuestion> q = quiz.getQuizQuestions();
		submissionService.submit(new MissionSubmissionCommand(user, quiz.getMission().getId(), UUID.randomUUID(), List.of(
				new MissionAnswerInput(q.get(0).getId(), correctOption(q.get(0)).getId(), null),
				new MissionAnswerInput(q.get(1).getId(), null, "겟또"),
				new MissionAnswerInput(q.get(2).getId(), correctOption(q.get(2)).getId(), null))));
	}

	private static QuizOption correctOption(QuizQuestion question) {
		return question.getOptions().stream().filter(QuizOption::isCorrect).findFirst().orElseThrow();
	}

	private static List<UUID> idsOf(PageResult<MissionSummary> page) {
		return page.getItems().stream().map(item -> item.getMission().getId()).toList();
	}

	@Test
	@DisplayName("목록은 시작 시각 내림차순으로 보여 주고 완료 표시는 요청한 사용자의 완료 제출만 반영한다")
	void listsNewestFirstWithCompletionOfTheRequestingUser() {
		// given
		MissionDetail oldest = saveSurveyStartingAt("가장 오래된 설문", Instant.parse("2026-09-01T00:00:00Z"));
		MissionDetail quiz = saveQuizMission("퀴즈");
		MissionDetail newest = saveSurveyStartingAt("가장 새 설문", Instant.parse("2026-11-01T00:00:00Z"));
		completeQuiz(userId, quiz);
		UUID other = seeds.user();
		// when
		PageResult<MissionSummary> mine = queryService.findMissions(userId, null, ALL);
		PageResult<MissionSummary> others = queryService.findMissions(other, null, ALL);
		// then
		List<UUID> ids = idsOf(mine);
		assertThat(ids).containsSubsequence(newest.getMission().getId(), quiz.getMission().getId(),
				oldest.getMission().getId());
		assertThat(mine.getItems()).filteredOn(item -> item.getMission().getId().equals(quiz.getMission().getId()))
				.singleElement().satisfies(item -> assertThat(item.isCompleted()).isTrue());
		assertThat(mine.getItems()).filteredOn(item -> item.getMission().getId().equals(newest.getMission().getId()))
				.singleElement().satisfies(item -> assertThat(item.isCompleted()).isFalse());
		assertThat(others.getItems()).filteredOn(item -> item.getMission().getId().equals(quiz.getMission().getId()))
				.singleElement().satisfies(item -> assertThat(item.isCompleted()).isFalse());
	}

	@Test
	@DisplayName("유형 조건은 해당 유형의 미션만 돌려주고 페이지 크기를 지킨다")
	void filtersByMissionTypeAndHonorsPageSize() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈");
		MissionDetail survey = saveSurveyMission("설문");
		// when
		PageResult<MissionSummary> quizzes = queryService.findMissions(userId, MissionType.QUIZ, ALL);
		PageResult<MissionSummary> firstOnly = queryService.findMissions(userId, null, new PageQuery(1, 1));
		// then
		assertThat(idsOf(quizzes)).contains(quiz.getMission().getId()).doesNotContain(survey.getMission().getId());
		assertThat(quizzes.getItems()).allSatisfy(item -> assertThat(item.getMission().getMissionType())
				.isEqualTo(MissionType.QUIZ));
		assertThat(firstOnly.getItems()).hasSize(1);
		assertThat(firstOnly.getTotalElements()).isGreaterThanOrEqualTo(2);
	}

	@Test
	@DisplayName("상세는 문항과 선택지를 표시 순서대로 돌려주고 완료한 사용자에게만 완료로 표시한다")
	void detailKeepsOrderAndShowsCompletionPerUser() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈");
		completeQuiz(userId, quiz);
		UUID other = seeds.user();
		// when
		MissionDetailView mine = queryService.findMission(userId, quiz.getMission().getId());
		MissionDetailView others = queryService.findMission(other, quiz.getMission().getId());
		// then
		assertThat(mine.isCompleted()).isTrue();
		assertThat(others.isCompleted()).isFalse();
		assertThat(mine.getDetail().getQuizQuestions()).extracting(QuizQuestion::getDisplayOrder)
				.containsExactly(1, 2, 3);
		assertThat(mine.getDetail().getQuizQuestions().get(0).getOptions()).extracting(QuizOption::getDisplayOrder)
				.containsExactly(1, 2);
	}

	@Test
	@DisplayName("없는 미션의 상세 조회는 MISSION-003이다")
	void detailOfMissingMissionIsNotFound() {
		// given
		UUID missing = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> queryService.findMission(userId, missing)).isInstanceOfSatisfying(MissionException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(MissionErrorCode.MISSION_NOT_FOUND));
	}

	@Test
	@DisplayName("시작 시각이 같은 미션은 DB의 BINARY(16) 정렬과 같이 ID 바이트를 부호 없이 비교한 내림차순으로 보여 준다")
	void ordersMissionsWithSameStartTimeByIdDescendingInDbByteOrder() {
		// given
		MissionDetail first = saveSurveyMission("같은 시각 설문 1");
		MissionDetail second = saveSurveyMission("같은 시각 설문 2");
		UUID higher = unsignedBytesCompare(first.getMission().getId(), second.getMission().getId()) > 0
				? first.getMission().getId() : second.getMission().getId();
		UUID lower = higher.equals(first.getMission().getId()) ? second.getMission().getId() : first.getMission().getId();
		// when
		PageResult<MissionSummary> result = queryService.findMissions(userId, MissionType.SURVEY, ALL);
		// then
		assertThat(idsOf(result)).containsSubsequence(higher, lower);
	}

	/** MySQL BINARY(16) 정렬과 같이 16바이트를 부호 없이 앞에서부터 비교한다. Java UUID.compareTo와 다를 수 있다. */
	private static int unsignedBytesCompare(UUID left, UUID right) {
		return Arrays.compareUnsigned(bytes(left), bytes(right));
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}

	@Test
	@DisplayName("페이지 오프셋이 int 범위를 넘어도 예외 없이 빈 목록과 전체 건수를 돌려준다")
	void hugePageReturnsEmptyItemsWithTotal() {
		// given
		saveSurveyStartingAt("목록", clock.instant());
		long total = queryService.findMissions(userId, null, ALL).getTotalElements();
		// when
		PageResult<MissionSummary> result = queryService.findMissions(userId, null,
				new PageQuery(Integer.MAX_VALUE, 100));
		// then
		assertThat(result.getItems()).isEmpty();
		assertThat(result.getTotalElements()).isEqualTo(total).isPositive();
		assertThat(result.getPage()).isEqualTo(Integer.MAX_VALUE);
	}
}
