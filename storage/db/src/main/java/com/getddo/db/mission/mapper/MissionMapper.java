package com.getddo.db.mission.mapper;

import java.util.List;
import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionRewardClaim;
import com.getddo.core.mission.domain.MissionSubmission;
import com.getddo.core.mission.domain.QuizAnswer;
import com.getddo.core.mission.domain.QuizOption;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.domain.QuizQuestionProgress;
import com.getddo.core.mission.domain.SurveyAnswer;
import com.getddo.core.mission.domain.SurveyOption;
import com.getddo.core.mission.domain.SurveyQuestion;
import com.getddo.db.mission.entity.MissionEntity;
import com.getddo.db.mission.entity.MissionQuizQuestionEntity;
import com.getddo.db.mission.entity.MissionRewardClaimEntity;
import com.getddo.db.mission.entity.MissionSubmissionEntity;
import com.getddo.db.mission.entity.QuizAnswerEntity;
import com.getddo.db.mission.entity.QuizQuestionOptionEntity;
import com.getddo.db.mission.entity.QuizQuestionProgressEntity;
import com.getddo.db.mission.entity.SurveyAnswerEntity;
import com.getddo.db.mission.entity.SurveyQuestionEntity;
import com.getddo.db.mission.entity.SurveyQuestionOptionEntity;

/**
 * 미션 Entity와 도메인 객체 변환. ID와 생성 시각은 영속화 때 채워지므로 Entity로는 옮기지 않는다.
 *
 * <p>문항·답변처럼 부모에 속하는 객체는 부모 ID를 도메인이 갖지 않으므로 Entity로 바꿀 때 별도 인자로 받는다.
 * 선택지와 답변 목록은 Entity가 갖지 않으므로 도메인으로 바꿀 때 별도 인자로 받는다.</p>
 */
@Mapper(componentModel = "spring")
public interface MissionMapper {

	MissionEntity toEntity(Mission mission);

	Mission toDomain(MissionEntity entity);

	MissionQuizQuestionEntity toEntity(QuizQuestion question, UUID missionId);

	QuizQuestion toDomain(MissionQuizQuestionEntity entity, List<QuizOption> options);

	QuizQuestionOptionEntity toEntity(QuizOption option, UUID quizQuestionId);

	QuizOption toDomain(QuizQuestionOptionEntity entity);

	SurveyQuestionEntity toEntity(SurveyQuestion question, UUID missionId);

	SurveyQuestion toDomain(SurveyQuestionEntity entity, List<SurveyOption> options);

	SurveyQuestionOptionEntity toEntity(SurveyOption option, UUID surveyQuestionId);

	SurveyOption toDomain(SurveyQuestionOptionEntity entity);

	MissionSubmissionEntity toEntity(MissionSubmission submission);

	MissionSubmission toDomain(MissionSubmissionEntity entity, List<QuizAnswer> quizAnswers,
			List<SurveyAnswer> surveyAnswers);

	@Mapping(target = "quizQuestionId", source = "answer.questionId")
	QuizAnswerEntity toEntity(QuizAnswer answer, UUID submissionId, UUID missionId);

	@Mapping(target = "questionId", source = "quizQuestionId")
	QuizAnswer toDomain(QuizAnswerEntity entity);

	@Mapping(target = "surveyQuestionId", source = "answer.questionId")
	SurveyAnswerEntity toEntity(SurveyAnswer answer, UUID submissionId, UUID missionId);

	@Mapping(target = "questionId", source = "surveyQuestionId")
	SurveyAnswer toDomain(SurveyAnswerEntity entity);

	@Mapping(target = "quizQuestionId", source = "questionId")
	QuizQuestionProgressEntity toEntity(QuizQuestionProgress progress);

	@Mapping(target = "questionId", source = "quizQuestionId")
	QuizQuestionProgress toDomain(QuizQuestionProgressEntity entity);

	MissionRewardClaimEntity toEntity(MissionRewardClaim claim);

	MissionRewardClaim toDomain(MissionRewardClaimEntity entity);
}
