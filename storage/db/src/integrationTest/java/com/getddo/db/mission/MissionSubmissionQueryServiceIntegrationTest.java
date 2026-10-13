package com.getddo.db.mission;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.mission.domain.MissionAnswerInput;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionSubmissionCommand;
import com.getddo.core.mission.domain.MissionSubmissionResult;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;
import com.getddo.core.mission.service.MissionSubmissionQueryService;
import com.getddo.core.mission.service.MissionSubmissionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 내 미션 제출 조회가 실제 MySQL의 제출·보상 청구·응모권 지급 행으로 결과를 맞게 만드는지 검증한다. */
class MissionSubmissionQueryServiceIntegrationTest extends MissionIntegrationTestSupport {

	private static final PageQuery FIRST_PAGE = new PageQuery(1, 20);

	@Autowired
	private MissionSubmissionQueryService queryService;
	@Autowired
	private MissionSubmissionService submissionService;

	/** 모든 문항에 정답(첫 선택지, 단답 "겟또")이거나 모든 문항에 오답(둘째 선택지, 틀린 단답)인 제출을 보낸다. */
	private MissionSubmissionResult submit(UUID user, MissionDetail quiz, boolean allCorrect) {
		List<QuizQuestion> q = quiz.getQuizQuestions();
		int pick = allCorrect ? 0 : 1;
		return submissionService.submit(new MissionSubmissionCommand(user, quiz.getMission().getId(), UUID.randomUUID(),
				List.of(new MissionAnswerInput(q.get(0).getId(), q.get(0).getOptions().get(pick).getId(), null),
						new MissionAnswerInput(q.get(1).getId(), null, allCorrect ? "겟또" : "오답"),
						new MissionAnswerInput(q.get(2).getId(), q.get(2).getOptions().get(pick).getId(), null))));
	}

	@Test
	@DisplayName("오답 뒤 완료한 사용자의 제출은 최신순으로 보이고 완료 제출에만 저장된 응모권 보상이 붙는다")
	void showsWrongThenCompletedSubmissionsNewestFirstWithRewardOnlyOnCompleted() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈");
		MissionSubmissionResult wrong = submit(userId, quiz, false);
		clock.set(clock.instant().plusSeconds(60));
		MissionSubmissionResult done = submit(userId, quiz, true);
		// when
		PageResult<MissionSubmissionResult> result = queryService.findMySubmissions(userId, quiz.getMission().getId(),
				FIRST_PAGE);
		// then
		assertThat(wrong.isCompleted()).isFalse();
		assertThat(done.isCompleted()).isTrue();
		assertThat(result.getItems()).extracting(MissionSubmissionResult::getSubmissionId)
				.containsExactly(done.getSubmissionId(), wrong.getSubmissionId());
		assertThat(result.getItems().get(0).getReward().getTicketCount()).isEqualTo(1);
		assertThat(result.getItems().get(0).getReward().getGrade()).isEqualTo(done.getReward().getGrade());
		assertThat(result.getItems().get(0).getReward().getGrantedAt()).isEqualTo(done.getReward().getGrantedAt());
		assertThat(result.getItems().get(0).isCreated()).isFalse();
		assertThat(result.getItems().get(1).getReward()).isNull();
		assertThat(result.getTotalElements()).isEqualTo(2);
	}

	@Test
	@DisplayName("다른 사용자의 제출은 보이지 않고 페이지 크기를 지킨다")
	void showsOnlyTheRequestingUsersSubmissionsAndHonorsPageSize() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈");
		UUID other = seeds.user();
		submit(userId, quiz, false);
		clock.set(clock.instant().plusSeconds(60));
		submit(userId, quiz, false);
		submit(other, quiz, false);
		// when
		PageResult<MissionSubmissionResult> mine = queryService.findMySubmissions(userId, quiz.getMission().getId(),
				new PageQuery(1, 1));
		PageResult<MissionSubmissionResult> others = queryService.findMySubmissions(other, quiz.getMission().getId(),
				FIRST_PAGE);
		PageResult<MissionSubmissionResult> nobody = queryService.findMySubmissions(seeds.user(),
				quiz.getMission().getId(), FIRST_PAGE);
		// then
		assertThat(mine.getItems()).hasSize(1);
		assertThat(mine.getTotalElements()).isEqualTo(2);
		assertThat(others.getTotalElements()).isEqualTo(1);
		assertThat(nobody.getItems()).isEmpty();
	}

	@Test
	@DisplayName("없는 미션은 MISSION-003이다")
	void missingMissionIsNotFound() {
		// given
		UUID missing = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> queryService.findMySubmissions(userId, missing, FIRST_PAGE))
				.isInstanceOfSatisfying(MissionException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(MissionErrorCode.MISSION_NOT_FOUND));
	}

	@Test
	@DisplayName("2페이지는 더 오래된 제출을 돌려주고 다른 사용자의 완료 제출과 보상은 목록에 섞이지 않는다")
	void secondPageHasOlderSubmissionAndOtherUsersCompletionDoesNotLeak() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈");
		UUID other = seeds.user();
		MissionSubmissionResult oldest = submit(userId, quiz, false);
		clock.set(clock.instant().plusSeconds(60));
		MissionSubmissionResult newest = submit(userId, quiz, false);
		submit(other, quiz, true);
		// when
		PageResult<MissionSubmissionResult> firstPage = queryService.findMySubmissions(userId,
				quiz.getMission().getId(), new PageQuery(1, 1));
		PageResult<MissionSubmissionResult> secondPage = queryService.findMySubmissions(userId,
				quiz.getMission().getId(), new PageQuery(2, 1));
		// then
		assertThat(firstPage.getItems()).extracting(MissionSubmissionResult::getSubmissionId)
				.containsExactly(newest.getSubmissionId());
		assertThat(secondPage.getItems()).extracting(MissionSubmissionResult::getSubmissionId)
				.containsExactly(oldest.getSubmissionId());
		assertThat(firstPage.getItems().get(0).getReward()).isNull();
		assertThat(secondPage.getItems().get(0).getReward()).isNull();
		assertThat(firstPage.getTotalElements()).isEqualTo(2);
	}
}
