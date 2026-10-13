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

/** 설문 응답 Entity. 완료된 응답은 덮어쓰지 않으므로 추가만 한다. */
@Getter
@Entity
@Table(name = "survey_answers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyAnswerEntity extends BaseEntity {

	@Column(name = "submission_id", nullable = false, updatable = false, length = 16)
	private UUID submissionId;

	@Column(name = "mission_id", nullable = false, updatable = false, length = 16)
	private UUID missionId;

	@Column(name = "survey_question_id", nullable = false, updatable = false, length = 16)
	private UUID surveyQuestionId;

	@Column(name = "selected_option_id", updatable = false, length = 16)
	private UUID selectedOptionId;

	@Column(name = "answer_text", updatable = false, columnDefinition = "text")
	private String answerText;

	@Builder
	private SurveyAnswerEntity(UUID submissionId, UUID missionId, UUID surveyQuestionId, UUID selectedOptionId,
			String answerText) {
		this.submissionId = submissionId;
		this.missionId = missionId;
		this.surveyQuestionId = surveyQuestionId;
		this.selectedOptionId = selectedOptionId;
		this.answerText = answerText;
	}
}
