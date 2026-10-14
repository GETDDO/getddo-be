package com.getddo.api.mission.dto.response;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import lombok.Builder;

import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionDetailView;
import com.getddo.core.mission.domain.MissionType;

/**
 * 미션 상세(M02)다. 목록의 필드를 모두 포함하고 설명과 문항·선택지를 더한다.
 *
 * <p>문항은 표시 순서 오름차순이다. 정답 정보는 포함하지 않는다({@link MissionQuestionResponse} 참고).</p>
 */
@Builder
public record MissionDetailResponse(
		UUID id,
		String title,
		String imageUrl,
		MissionType missionType,
		Instant startsAt,
		Instant endsAt,
		int rewardTicketCount,
		boolean completed,
		Instant serverTime,
		String description,
		List<MissionQuestionResponse> questions) {

	/** 상세 조회 결과를 응답으로 옮긴다. */
	public static MissionDetailResponse from(MissionDetailView view, Instant serverTime) {
		MissionDetail detail = view.getDetail();
		Mission mission = detail.getMission();
		MissionSummaryResponse summary = MissionSummaryResponse.of(mission, view.isCompleted(), serverTime);
		List<MissionQuestionResponse> questions = Stream.concat(
						detail.getQuizQuestions().stream().map(MissionQuestionResponse::from),
						detail.getSurveyQuestions().stream().map(MissionQuestionResponse::from))
				.sorted(Comparator.comparingInt(MissionQuestionResponse::displayOrder))
				.toList();
		return MissionDetailResponse.builder()
				.id(summary.id())
				.title(summary.title())
				.imageUrl(summary.imageUrl())
				.missionType(summary.missionType())
				.startsAt(summary.startsAt())
				.endsAt(summary.endsAt())
				.rewardTicketCount(summary.rewardTicketCount())
				.completed(summary.completed())
				.serverTime(summary.serverTime())
				.description(mission.getDescription())
				.questions(questions)
				.build();
	}
}
