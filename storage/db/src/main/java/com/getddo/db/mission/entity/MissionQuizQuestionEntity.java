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

import com.getddo.core.mission.domain.QuizQuestionType;
import com.getddo.db.common.entity.BaseEntity;

/** 퀴즈 문항 Entity. 운영 중인 미션의 문항은 수정하지 않는다. */
@Getter
@Entity
@Table(name = "mission_quiz_questions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionQuizQuestionEntity extends BaseEntity {

	@Column(name = "mission_id", nullable = false, updatable = false, length = 16)
	private UUID missionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "question_type", nullable = false, updatable = false)
	private QuizQuestionType questionType;

	@Column(name = "question_text", nullable = false, updatable = false, columnDefinition = "text")
	private String questionText;

	@Column(name = "display_order", nullable = false, updatable = false)
	private int displayOrder;

	/** 단답형 정답. 선택형이면 null. */
	@Column(name = "correct_answer", updatable = false, columnDefinition = "text")
	private String correctAnswer;

	@Builder
	private MissionQuizQuestionEntity(UUID missionId, QuizQuestionType questionType, String questionText,
			int displayOrder, String correctAnswer) {
		this.missionId = missionId;
		this.questionType = questionType;
		this.questionText = questionText;
		this.displayOrder = displayOrder;
		this.correctAnswer = correctAnswer;
	}
}
