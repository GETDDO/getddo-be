package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 사용자가 퀴즈 문항을 정답으로 완료한 기록. 사용자·문항 단위로 한 번만 남는다.
 *
 * <p>정답으로 완료한 문항을 다시 풀어도 추가로 기록하지 않는다. 근거가 된 정답 답변을 가리킨다.</p>
 */
@Getter
public final class QuizQuestionProgress {

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID questionId;
	private final UUID correctAnswerId;
	private final UUID userId;
	/** 저장 전에는 null. */
	private final Instant createdAt;

	public QuizQuestionProgress(UUID id, UUID questionId, UUID correctAnswerId, UUID userId, Instant createdAt) {
		this.id = id;
		this.questionId = Objects.requireNonNull(questionId, "questionId");
		this.correctAnswerId = Objects.requireNonNull(correctAnswerId, "correctAnswerId");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.createdAt = createdAt;
	}

	/**
	 * 저장된 제출에 포함된 정답 답변을 근거로 진행 기록을 만든다. 사용자는 제출의 사용자다.
	 *
	 * @throws com.getddo.core.mission.exception.MissionException 정답이 아닌 답변이거나 그 제출에 포함된 답변이 아닐 때
	 */
	public static QuizQuestionProgress of(MissionSubmission submission, QuizAnswer correctAnswer) {
		Objects.requireNonNull(submission, "submission");
		Objects.requireNonNull(correctAnswer.getId(), "answer.id");
		MissionRules.answer(correctAnswer.isCorrect());
		MissionRules.answer(submission.getQuizAnswers().stream()
				.anyMatch(answer -> correctAnswer.getId().equals(answer.getId())));
		return new QuizQuestionProgress(null, correctAnswer.getQuestionId(), correctAnswer.getId(),
				submission.getUserId(), null);
	}
}
