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

/** 사용자가 퀴즈 문항을 정답으로 완료한 기록 Entity. {@code (user_id, quiz_question_id)}가 유일하다. */
@Getter
@Entity
@Table(name = "quiz_question_progress")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestionProgressEntity extends BaseEntity {

	@Column(name = "quiz_question_id", nullable = false, updatable = false, length = 16)
	private UUID quizQuestionId;

	@Column(name = "correct_answer_id", nullable = false, updatable = false, length = 16)
	private UUID correctAnswerId;

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Builder
	private QuizQuestionProgressEntity(UUID quizQuestionId, UUID correctAnswerId, UUID userId) {
		this.quizQuestionId = quizQuestionId;
		this.correctAnswerId = correctAnswerId;
		this.userId = userId;
	}
}
