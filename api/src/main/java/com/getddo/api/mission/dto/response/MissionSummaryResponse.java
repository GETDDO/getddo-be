package com.getddo.api.mission.dto.response;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionRewardClaim;
import com.getddo.core.mission.domain.MissionSummary;
import com.getddo.core.mission.domain.MissionType;

/**
 * 미션 목록(M01)의 한 건이다. 문항은 포함하지 않는다.
 *
 * <p>상태가 아니라 기간({@code startsAt}, {@code endsAt})과 {@code completed}로 진행 여부를 판단한다.
 * {@code rewardTicketCount}는 미션 보상이 항상 응모권 1장이라 1로 고정이며, 지급 등급은 완료할 때 정해진다.
 * {@code imageUrl}은 이미지 저장소가 정해지기 전이라 이벤트 조회와 같이 항상 null이다.</p>
 *
 * @param completed 요청한 사용자가 이 미션을 완료했는지 여부
 * @param serverTime 응답을 만든 서버 시각
 */
@Builder
public record MissionSummaryResponse(
		UUID id,
		String title,
		String imageUrl,
		MissionType missionType,
		Instant startsAt,
		Instant endsAt,
		int rewardTicketCount,
		boolean completed,
		Instant serverTime) {

	/** 목록의 미션 요약을 응답으로 옮긴다. */
	public static MissionSummaryResponse from(MissionSummary summary, Instant serverTime) {
		return of(summary.getMission(), summary.isCompleted(), serverTime);
	}

	static MissionSummaryResponse of(Mission mission, boolean completed, Instant serverTime) {
		return MissionSummaryResponse.builder()
				.id(mission.getId())
				.title(mission.getTitle())
				.imageUrl(null)
				.missionType(mission.getMissionType())
				.startsAt(mission.getStartsAt())
				.endsAt(mission.getEndsAt())
				.rewardTicketCount(MissionRewardClaim.REWARD_TICKET_COUNT)
				.completed(completed)
				.serverTime(serverTime)
				.build();
	}
}
