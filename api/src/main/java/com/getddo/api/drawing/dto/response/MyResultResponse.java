package com.getddo.api.drawing.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.api.event.dto.response.PrizeResponse;
import com.getddo.core.drawing.domain.MyDrawResultStatus;
import com.getddo.core.drawing.domain.UserDrawResult;

public record MyResultResponse(UUID eventId, MyDrawResultStatus result, PrizeResponse prize,
		boolean isPublished, Integer revision, Instant serverTime) {
	public static MyResultResponse from(UserDrawResult.MyResult result) {
		return new MyResultResponse(result.getEventId(), result.getResult(),
				result.getPrize() == null ? null : PrizeResponse.from(result.getPrize()),
				result.isPublished(), result.getRevision(), result.getServerTime());
	}
}
