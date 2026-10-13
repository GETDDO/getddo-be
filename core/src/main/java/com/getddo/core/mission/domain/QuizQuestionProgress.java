package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;

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
	 * 저장된 제출에 포함된 정답 답변을 근거로 진행 기록을 만든다. 사용자와 문항, 정답 여부는 호출자가 넘긴 값이 아니라 제출에
	 * 저장된 답변에서 읽는다.
	 *
	 * @param submission   저장된 제출
	 * @param quizAnswerId 근거가 되는 정답 답변의 ID
	 * @throws com.getddo.core.mission.exception.MissionException 그 제출에 없는 답변이거나 정답이 아닌 답변일 때
	 */
	public static QuizQuestionProgress of(MissionSubmission submission, UUID quizAnswerId) {
		Objects.requireNonNull(submission, "submission");
		Objects.requireNonNull(quizAnswerId, "quizAnswerId");
		QuizAnswer stored = submission.getQuizAnswers().stream()
				.filter(answer -> quizAnswerId.equals(answer.getId()))
				.findFirst()
				.orElseThrow(() -> new MissionException(MissionErrorCode.INVALID_MISSION_ANSWER));
		MissionRules.answer(stored.isCorrect());
		return new QuizQuestionProgress(null, stored.getQuestionId(), stored.getId(), submission.getUserId(), null);
	}
}
