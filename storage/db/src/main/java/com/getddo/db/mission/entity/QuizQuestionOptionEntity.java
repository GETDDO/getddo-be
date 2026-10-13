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

/** 퀴즈 선택지 Entity. */
@Getter
@Entity
@Table(name = "quiz_question_options")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestionOptionEntity extends BaseEntity {

	@Column(name = "quiz_question_id", nullable = false, updatable = false, length = 16)
	private UUID quizQuestionId;

	@Column(name = "option_text", nullable = false, updatable = false, columnDefinition = "text")
	private String optionText;

	@Column(name = "display_order", nullable = false, updatable = false)
	private int displayOrder;

	@Column(name = "is_correct", nullable = false, updatable = false)
	private boolean correct;

	@Builder
	private QuizQuestionOptionEntity(UUID quizQuestionId, String optionText, int displayOrder, boolean correct) {
		this.quizQuestionId = quizQuestionId;
		this.optionText = optionText;
		this.displayOrder = displayOrder;
		this.correct = correct;
	}
}
