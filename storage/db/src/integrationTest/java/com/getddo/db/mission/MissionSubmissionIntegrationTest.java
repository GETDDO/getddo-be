package com.getddo.db.mission;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionRewardClaim;
import com.getddo.core.mission.domain.MissionSubmission;
import com.getddo.core.mission.domain.QuizAnswer;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.domain.QuizQuestionProgress;
import com.getddo.core.mission.domain.SurveyAnswer;
import com.getddo.core.mission.domain.SurveyQuestion;
import com.getddo.core.mission.repository.MissionRewardClaimRepository;
import com.getddo.core.mission.repository.MissionSubmissionRepository;
import com.getddo.core.mission.repository.QuizQuestionProgressRepository;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 제출·답변·정답 진행·보상 청구의 Entity 매핑, 유일 제약과 일관성을 실제 MySQL 스키마에서 검증한다. */
class MissionSubmissionIntegrationTest extends MissionIntegrationTestSupport {

	@Autowired
	private MissionSubmissionRepository submissionRepository;
	@Autowired
	private QuizQuestionProgressRepository progressRepository;
	@Autowired
	private MissionRewardClaimRepository claimRepository;

	private MissionSubmission submitQuiz(MissionDetail detail, UUID user, boolean completed) {
		QuizQuestion shortAnswer = detail.getQuizQuestions().get(1);
		MissionSubmission submission = MissionSubmission.of(detail.getMission(), user, completed,
				List.of(QuizAnswer.of(shortAnswer.getId(), null, completed ? "겟또" : "오답", completed)), List.of());
		return transaction.execute(status -> submissionRepository.insert(submission));
	}

	@Test
	@DisplayName("퀴즈 제출을 저장하면 서버가 받은 시각과 답변 ID가 채워지고 다시 읽으면 답변이 그대로다")
	void insertsQuizSubmissionWithAnswers() {
		// given
		MissionDetail mission = saveQuizMission("퀴즈");
		QuizQuestion choice = mission.getQuizQuestions().get(0);
		UUID correctOption = choice.getOptions().get(0).getId();
		MissionSubmission submission = MissionSubmission.of(mission.getMission(), userId, true, List.of(
				QuizAnswer.of(choice.getId(), correctOption, null, true),
				QuizAnswer.of(mission.getQuizQuestions().get(1).getId(), null, "겟또", true)), List.of());
		clock.set(clock.instant().plusSeconds(30));
		// when
		MissionSubmission saved = transaction.execute(status -> submissionRepository.insert(submission));
		// then
		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isEqualTo(clock.instant());
		assertThat(saved.getRewardPolicyId()).isEqualTo(rewardPolicyId);
		assertThat(saved.getQuizAnswers()).hasSize(2).allSatisfy(answer -> assertThat(answer.getId()).isNotNull());
		MissionSubmission found = submissionRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.isCompleted()).isTrue();
		assertThat(found.getUserId()).isEqualTo(userId);
		assertThat(found.getCreatedAt()).isEqualTo(saved.getCreatedAt());
		assertThat(found.getQuizAnswers()).extracting(QuizAnswer::getQuestionId)
				.containsExactlyInAnyOrder(choice.getId(), mission.getQuizQuestions().get(1).getId());
		assertThat(found.getQuizAnswers()).filteredOn(a -> a.getSelectedOptionId() != null)
				.singleElement().satisfies(a -> {
					assertThat(a.getSelectedOptionId()).isEqualTo(correctOption);
					assertThat(a.getAnswerText()).isNull();
					assertThat(a.isCorrect()).isTrue();
				});
		assertThat(found.getQuizAnswers()).filteredOn(a -> a.getAnswerText() != null)
				.singleElement().satisfies(a -> assertThat(a.getAnswerText()).isEqualTo("겟또"));
		assertThat(found.getSurveyAnswers()).isEmpty();
	}

	@Test
	@DisplayName("설문 제출을 저장하면 선택 답변과 서술 답변을 그대로 돌려준다")
	void insertsSurveySubmission() {
		// given
		MissionDetail mission = saveSurveyMission("설문");
		SurveyQuestion choice = mission.getSurveyQuestions().get(0);
		SurveyQuestion text = mission.getSurveyQuestions().get(1);
		UUID option = choice.getOptions().get(1).getId();
		MissionSubmission submission = MissionSubmission.of(mission.getMission(), userId, true, List.of(), List.of(
				SurveyAnswer.of(choice.getId(), option, null), SurveyAnswer.of(text.getId(), null, "좋아요")));
		// when
		MissionSubmission saved = transaction.execute(status -> submissionRepository.insert(submission));
		// then
		MissionSubmission found = submissionRepository.findById(saved.getId()).orElseThrow();
		assertThat(found.getSurveyAnswers()).hasSize(2);
		assertThat(found.getSurveyAnswers()).filteredOn(a -> a.getQuestionId().equals(choice.getId()))
				.singleElement().satisfies(a -> assertThat(a.getSelectedOptionId()).isEqualTo(option));
		assertThat(found.getSurveyAnswers()).filteredOn(a -> a.getQuestionId().equals(text.getId()))
				.singleElement().satisfies(a -> assertThat(a.getAnswerText()).isEqualTo("좋아요"));
		assertThat(found.getQuizAnswers()).isEmpty();
	}

	@Test
	@DisplayName("다른 미션의 문항에 답한 제출은 소속 미션을 맞추는 복합 외래 키가 막는다")
	void rejectsAnswerOfOtherMissionsQuestion() {
		// given
		MissionDetail mine = saveQuizMission("내 퀴즈");
		MissionDetail other = saveQuizMission("다른 퀴즈");
		MissionSubmission submission = MissionSubmission.of(mine.getMission(), userId, false,
				List.of(QuizAnswer.of(other.getQuizQuestions().get(1).getId(), null, "오답", false)), List.of());
		// when
		// then
		assertThatThrownBy(() -> transaction.execute(status -> submissionRepository.insert(submission)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(count("select count(*) from mission_submissions where user_id = ?", bytes(userId))).isZero();
	}

	@Test
	@DisplayName("완료한 제출만 완료로 찾고 미완료 제출이나 다른 사용자의 완료는 찾지 않는다")
	void findsOnlyOwnCompletedSubmission() {
		// given
		MissionDetail mission = saveQuizMission("퀴즈");
		UUID other = seeds.user();
		submitQuiz(mission, userId, false);
		submitQuiz(mission, other, true);
		// when
		// then
		assertThat(submissionRepository.findCompleted(userId, mission.getMission().getId())).isEmpty();
		MissionSubmission completed = submitQuiz(mission, userId, true);
		assertThat(submissionRepository.findCompleted(userId, mission.getMission().getId()))
				.hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(completed.getId()));
	}

	@Test
	@DisplayName("내 제출은 최신순으로 페이지를 나누고 다른 사용자·다른 미션의 제출은 섞이지 않는다")
	void listsOwnSubmissionsNewestFirst() {
		// given
		MissionDetail mission = saveQuizMission("퀴즈");
		MissionDetail otherMission = saveQuizMission("다른 퀴즈");
		UUID other = seeds.user();
		MissionSubmission first = submitQuiz(mission, userId, false);
		clock.set(clock.instant().plus(Duration.ofMinutes(1)));
		MissionSubmission second = submitQuiz(mission, userId, false);
		clock.set(clock.instant().plus(Duration.ofMinutes(1)));
		MissionSubmission third = submitQuiz(mission, userId, true);
		submitQuiz(mission, other, true);
		submitQuiz(otherMission, userId, true);
		// when
		PageResult<MissionSubmission> page1 = submissionRepository.findByUser(userId, mission.getMission().getId(),
				new PageQuery(1, 2));
		PageResult<MissionSubmission> page2 = submissionRepository.findByUser(userId, mission.getMission().getId(),
				new PageQuery(2, 2));
		// then
		assertThat(page1.getTotalElements()).isEqualTo(3);
		assertThat(page1.getItems()).extracting(MissionSubmission::getId).containsExactly(third.getId(), second.getId());
		assertThat(page2.getItems()).extracting(MissionSubmission::getId).containsExactly(first.getId());
		assertThat(page1.getItems()).allSatisfy(s -> assertThat(s.getQuizAnswers()).hasSize(1));
	}

	@Test
	@DisplayName("목록 화면용으로 주어진 미션 중 내가 완료한 미션 ID만 모아 준다")
	void findsCompletedMissionIds() {
		// given
		MissionDetail done = saveQuizMission("완료");
		MissionDetail tried = saveQuizMission("도전 중");
		MissionDetail untouched = saveQuizMission("안 함");
		submitQuiz(done, userId, true);
		submitQuiz(tried, userId, false);
		List<UUID> ids = List.of(done.getMission().getId(), tried.getMission().getId(),
				untouched.getMission().getId());
		// when
		Set<UUID> completed = submissionRepository.findCompletedMissionIds(userId, ids);
		// then
		assertThat(completed).containsExactly(done.getMission().getId());
		assertThat(submissionRepository.findCompletedMissionIds(userId, List.of())).isEmpty();
	}

	@Test
	@DisplayName("정답으로 완료한 문항은 사용자·문항 단위로 한 번만 기록하고 같은 문항을 또 기록하면 막는다")
	void recordsCorrectQuestionOnce() {
		// given
		MissionDetail mission = saveQuizMission("퀴즈");
		MissionSubmission saved = submitQuiz(mission, userId, true);
		QuizAnswer correct = saved.getQuizAnswers().get(0);
		QuizQuestion question = mission.getQuizQuestions().get(1);
		UUID untouchedQuestion = mission.getQuizQuestions().get(0).getId();
		// when
		QuizQuestionProgress progress = progressRepository.insert(QuizQuestionProgress.of(saved, correct.getId()));
		// then
		assertThat(progress.getId()).isNotNull();
		assertThat(progress.getCorrectAnswerId()).isEqualTo(correct.getId());
		assertThat(progressRepository.findCorrectQuestionIds(userId, List.of(question.getId(), untouchedQuestion)))
				.containsExactly(question.getId());
		assertThat(progressRepository.findCorrectQuestionIds(seeds.user(), List.of(question.getId()))).isEmpty();
		assertThat(progressRepository.findCorrectQuestionIds(userId, List.of())).isEmpty();
		assertThatThrownBy(() -> progressRepository.insert(QuizQuestionProgress.of(saved, correct.getId())))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("보상 청구는 완료 제출의 미션·사용자·정책을 그대로 담고 응모권 1장으로 저장된다")
	void insertsRewardClaimConsistentWithSubmission() {
		// given
		MissionDetail mission = saveQuizMission("퀴즈");
		MissionSubmission completed = submitQuiz(mission, userId, true);
		// when
		MissionRewardClaim saved = claimRepository.insert(MissionRewardClaim.of(completed));
		// then
		MissionRewardClaim found = claimRepository.findByUserAndMission(userId, mission.getMission().getId())
				.orElseThrow();
		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getMissionSubmissionId()).isEqualTo(completed.getId());
		assertThat(found.getMissionId()).isEqualTo(completed.getMissionId());
		assertThat(found.getUserId()).isEqualTo(userId);
		assertThat(found.getRewardPolicyId()).isEqualTo(rewardPolicyId);
		assertThat(found.getTicketCount()).isEqualTo(1);
		assertThat(found.getSourceKey()).isEqualTo(MissionRewardClaim.sourceKey(mission.getMission().getId()));
		assertThat(found.getCreatedAt()).isEqualTo(clock.instant());
		assertThat(claimRepository.findByUserAndMission(seeds.user(), mission.getMission().getId())).isEmpty();
	}

	@Test
	@DisplayName("같은 사용자가 같은 미션의 보상을 두 번 청구하면 유일 제약이 막고 다른 미션은 청구할 수 있다")
	void rewardClaimIsUniquePerUserAndMission() {
		// given
		MissionDetail mission = saveQuizMission("퀴즈");
		MissionDetail another = saveQuizMission("다른 퀴즈");
		MissionSubmission first = submitQuiz(mission, userId, true);
		MissionSubmission again = submitQuiz(mission, userId, true);
		claimRepository.insert(MissionRewardClaim.of(first));
		// when
		// then
		assertThatThrownBy(() -> claimRepository.insert(MissionRewardClaim.of(again)))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(claimRepository.insert(MissionRewardClaim.of(submitQuiz(another, userId, true))).getId()).isNotNull();
		assertThat(count("select count(*) from mission_reward_claims where user_id = ?", bytes(userId))).isEqualTo(2);
		Mission unchanged = mission.getMission();
		assertThat(unchanged.getId()).isEqualTo(first.getMissionId());
	}
}
