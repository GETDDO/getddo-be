package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.Getter;

/** 관리자가 등록할 미션. 퀴즈 미션은 퀴즈 문항만, 설문 미션은 설문 문항만 담는다. 서버가 모든 ID를 만든다. */
@Getter
public final class MissionRegistration {

	private final String title;
	private final String description;
	private final MissionType missionType;
	private final Instant startsAt;
	private final Instant endsAt;
	/** 설명 이미지 경로. 없으면 null. */
	private final String imageKey;
	private final List<QuizQuestion> quizQuestions;
	private final List<SurveyQuestion> surveyQuestions;

	public MissionRegistration(String title, String description, MissionType missionType, Instant startsAt,
			Instant endsAt, String imageKey, List<QuizQuestion> quizQuestions, List<SurveyQuestion> surveyQuestions) {
		this.title = Objects.requireNonNull(title, "title");
		this.description = Objects.requireNonNull(description, "description");
		this.missionType = Objects.requireNonNull(missionType, "missionType");
		this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
		this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
		this.imageKey = imageKey;
		this.quizQuestions = List.copyOf(quizQuestions);
		this.surveyQuestions = List.copyOf(surveyQuestions);
	}
}
