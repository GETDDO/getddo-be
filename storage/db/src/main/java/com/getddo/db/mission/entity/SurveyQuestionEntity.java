package com.getddo.db.mission.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.core.mission.domain.SurveyQuestionType;
import com.getddo.db.common.entity.BaseUpdatableEntity;

/** 설문 문항 Entity. 운영 중인 미션의 문항은 수정하지 않는다. */
@Getter
@Entity
@Table(name = "survey_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyQuestionEntity extends BaseUpdatableEntity {

	@Column(name = "mission_id", nullable = false, updatable = false, length = 16)
	private UUID missionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "question_type", nullable = false, updatable = false)
	private SurveyQuestionType questionType;

	@Column(name = "question_text", nullable = false, updatable = false, columnDefinition = "text")
	private String questionText;

	@Column(name = "is_required", nullable = false, updatable = false)
	private boolean required;

	@Column(name = "display_order", nullable = false, updatable = false)
	private int displayOrder;

	@Builder
	private SurveyQuestionEntity(UUID missionId, SurveyQuestionType questionType, String questionText,
			boolean required, int displayOrder) {
		this.missionId = missionId;
		this.questionType = questionType;
		this.questionText = questionText;
		this.required = required;
		this.displayOrder = displayOrder;
	}
}
