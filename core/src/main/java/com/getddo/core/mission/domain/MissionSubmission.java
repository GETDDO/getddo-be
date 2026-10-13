package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;

/**
 * 사용자가 미션에 제출한 기록. 저장 후에는 바꾸지 않는다.
 *
 * <p>정답을 맞히지 못한 퀴즈 제출도 미완료({@code completed=false})로 남는다. 설문은 필수 문항을 모두 채운 최종 제출만
 * 완료로 기록한다. {@code createdAt}은 서버가 제출을 받아 저장한 시각이다.</p>
 */
@Getter
public final class MissionSubmission {

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID missionId;
	private final UUID rewardPolicyId;
	private final UUID userId;
	private final boolean completed;
	/** 저장 전에는 null. */
	private final Instant createdAt;
	private final List<QuizAnswer> quizAnswers;
	private final List<SurveyAnswer> surveyAnswers;

	public MissionSubmission(UUID id, UUID missionId, UUID rewardPolicyId, UUID userId, boolean completed,
			Instant createdAt, List<QuizAnswer> quizAnswers, List<SurveyAnswer> surveyAnswers) {
		this.id = id;
		this.missionId = Objects.requireNonNull(missionId, "missionId");
		this.rewardPolicyId = Objects.requireNonNull(rewardPolicyId, "rewardPolicyId");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.completed = completed;
		this.createdAt = createdAt;
		this.quizAnswers = List.copyOf(quizAnswers);
		this.surveyAnswers = List.copyOf(surveyAnswers);
		// 저장소가 복원하는 경로와 팩터리가 같은 규칙을 지키도록 미션 유형 없이 판단할 수 있는 규칙은 여기서 검사한다.
		MissionRules.answer(distinct(this.quizAnswers.stream().map(QuizAnswer::getQuestionId).toList()));
		MissionRules.answer(distinct(this.surveyAnswers.stream().map(SurveyAnswer::getQuestionId).toList()));
		// 오답은 완료·보상으로 기록하지 않는다. 다문항 완료 조건은 채점 세부 계약이 정하므로 최소 불변식만 지킨다.
		MissionRules.answer(!completed || this.quizAnswers.stream().allMatch(QuizAnswer::isCorrect));
	}

	/**
	 * 저장된 미션에 대한 새 제출을 만든다. 미션의 보상 정책을 제출에 함께 기록한다. 같은 문항에 두 번 답했거나 완료한 제출에
	 * 오답이 있는 경우의 거절은 생성자가 맡고, 이 팩터리는 미션 유형이 있어야 판단할 수 있는 규칙을 더한다.
	 *
	 * @throws com.getddo.core.mission.exception.MissionException 미션 유형과 다른 종류의 답변이 있거나, 같은 문항에 두 번 답했거나,
	 *         완료한 퀴즈 제출에 답변이 없거나 오답이 있을 때
	 */
	public static MissionSubmission of(Mission mission, UUID userId, boolean completed, List<QuizAnswer> quizAnswers,
			List<SurveyAnswer> surveyAnswers) {
		Objects.requireNonNull(mission.getId(), "mission.id");
		MissionRules.answer(mission.getMissionType() == MissionType.QUIZ
				? surveyAnswers.isEmpty() : quizAnswers.isEmpty());
		if (completed && mission.getMissionType() == MissionType.QUIZ) {
			// 완료한 퀴즈 제출은 답변이 있어야 한다. 오답이 섞였는지는 생성자가 검사한다.
			MissionRules.answer(!quizAnswers.isEmpty());
		}
		return new MissionSubmission(null, mission.getId(), mission.getRewardPolicyId(), userId, completed, null,
				quizAnswers, surveyAnswers);
	}

	private static boolean distinct(List<UUID> questionIds) {
		Set<UUID> seen = new HashSet<>();
		return questionIds.stream().allMatch(seen::add);
	}
}
