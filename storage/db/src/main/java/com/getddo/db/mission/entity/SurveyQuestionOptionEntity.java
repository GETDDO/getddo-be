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

/** 설문 선택지 Entity. */
@Getter
@Entity
@Table(name = "survey_question_options")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SurveyQuestionOptionEntity extends BaseEntity {

	@Column(name = "survey_question_id", nullable = false, updatable = false, length = 16)
	private UUID surveyQuestionId;

	@Column(name = "option_text", nullable = false, updatable = false, columnDefinition = "text")
	private String optionText;

	@Column(name = "display_order", nullable = false, updatable = false)
	private int displayOrder;

	@Builder
	private SurveyQuestionOptionEntity(UUID surveyQuestionId, String optionText, int displayOrder) {
		this.surveyQuestionId = surveyQuestionId;
		this.optionText = optionText;
		this.displayOrder = displayOrder;
	}
}
