package com.getddo.db.mission.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.db.common.entity.BaseEntity;

/**
 * 퀴즈 문항 답변과 서버 채점 결과 Entity. 추가만 하고 수정하지 않는다.
 *
 * <p>{@code missionId}는 제출과 문항이 같은 미션에 속함을 복합 외래 키로 보장하려고 둔다.</p>
 */
@Getter
@Entity
@Table(name = "quiz_answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAnswerEntity extends BaseEntity {

	@Column(name = "submission_id", nullable = false, updatable = false, length = 16)
	private UUID submissionId;

	@Column(name = "quiz_question_id", nullable = false, updatable = false, length = 16)
	private UUID quizQuestionId;

	@Column(name = "selected_option_id", updatable = false, length = 16)
	private UUID selectedOptionId;

	@Column(name = "mission_id", nullable = false, updatable = false, length = 16)
	private UUID missionId;

	@Column(name = "answer_text", updatable = false, columnDefinition = "text")
	private String answerText;

	@Column(name = "is_correct", nullable = false, updatable = false)
	private boolean correct;

	@Builder
	private QuizAnswerEntity(UUID submissionId, UUID quizQuestionId, UUID selectedOptionId, UUID missionId,
			String answerText, boolean correct) {
		this.submissionId = submissionId;
		this.quizQuestionId = quizQuestionId;
		this.selectedOptionId = selectedOptionId;
		this.missionId = missionId;
		this.answerText = answerText;
		this.correct = correct;
	}
}
