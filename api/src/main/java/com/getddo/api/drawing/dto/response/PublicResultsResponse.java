package com.getddo.api.drawing.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawDisplayStatus;
import com.getddo.core.drawing.domain.UserDrawResult;

/** 공개 계약에 필요한 마스킹 정보만 응답한다. */
public record PublicResultsResponse(UUID eventId, boolean isPublished, Instant publishedAt, Integer revision,
		Instant updatedAt, Instant serverTime, DrawDisplayStatus displayStatus, List<PublishedPrize> prizes) {
	public static PublicResultsResponse from(UserDrawResult.PublicResults result) {
		return new PublicResultsResponse(result.getEventId(), result.isPublished(), result.getPublishedAt(),
				result.getRevision(), result.getUpdatedAt(), result.getServerTime(), result.getDisplayStatus(),
				result.getPrizes().stream().map(PublishedPrize::from).toList());
	}

	public record PublishedPrize(UUID prizeId, int rank, String name, int winnerCount, int unfilledCount,
			List<MaskedWinner> winners) {
		static PublishedPrize from(UserDrawResult.PublishedPrize prize) {
			return new PublishedPrize(prize.getPrizeId(), prize.getRank(), prize.getName(), prize.getWinnerCount(),
					prize.getUnfilledCount(), prize.getWinners().stream()
							.map(winner -> new MaskedWinner(winner.getMaskedName(), winner.getMaskedPhoneNum())).toList());
		}
	}

	public record MaskedWinner(String maskedName, String maskedPhoneNum) { }
}
