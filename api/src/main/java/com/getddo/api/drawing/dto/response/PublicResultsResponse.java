package com.getddo.api.drawing.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawDisplayStatus;
import com.getddo.core.drawing.domain.UserDrawResult;

/**
 * 공개 결과 조회의 응답으로, 내부 추첨 근거 없이 공개 명단과 마스킹 정보만 제공한다.
 *
 * @param publishedAt 최초 공개 시각, 공개 전에는 null
 * @param revision 최신 공개 버전, 공개 전에는 null
 * @param updatedAt 최신 공개 버전 반영 시각, 공개 전에는 null
 * @param prizes 화면 발표 순서로 정렬된 경품 목록, 공개 전에는 빈 목록
 */
public record PublicResultsResponse(UUID eventId, boolean isPublished, Instant publishedAt, Integer revision,
		Instant updatedAt, Instant serverTime, DrawDisplayStatus displayStatus, List<PublishedPrize> prizes) {
	/** 서비스의 공개 상태와 경품 순서를 유지하면서 HTTP 계약에 필요한 필드만 옮긴다. */
	public static PublicResultsResponse from(UserDrawResult.PublicResults result) {
		return new PublicResultsResponse(result.getEventId(), result.isPublished(), result.getPublishedAt(),
				result.getRevision(), result.getUpdatedAt(), result.getServerTime(), result.getDisplayStatus(),
				result.getPrizes().stream().map(PublishedPrize::from).toList());
	}

	/** 경품의 설정 인원·미충원 수와 해당 공개 버전에 포함된 마스킹 당첨자 명단이다. */
	public record PublishedPrize(UUID prizeId, int rank, String name, int winnerCount, int unfilledCount,
			List<MaskedWinner> winners) {
		/** 경품 연결용 내부 식별자는 당첨자 응답에 옮기지 않고 마스킹 정보만 선택한다. */
		static PublishedPrize from(UserDrawResult.PublishedPrize prize) {
			return new PublishedPrize(prize.getPrizeId(), prize.getRank(), prize.getName(), prize.getWinnerCount(),
					prize.getUnfilledCount(), prize.getWinners().stream()
							.map(winner -> new MaskedWinner(winner.getMaskedName(), winner.getMaskedPhoneNum())).toList());
		}
	}

	/** 이름은 마스킹된 값, 전화번호는 끝 네 자리만 담고 원본 개인정보는 보유하지 않는다. */
	public record MaskedWinner(String maskedName, String maskedPhoneNum) { }
}
