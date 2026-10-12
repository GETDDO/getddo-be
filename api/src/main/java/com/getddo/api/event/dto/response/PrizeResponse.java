package com.getddo.api.event.dto.response;

import java.util.UUID;

import com.getddo.core.event.domain.RegisteredEvent;

/** 사용자와 관리자에게 제공하는 공용 경품 정보. */
public record PrizeResponse(UUID id, int rank, String name, String description, String imageUrl, int winnerCount) {
	public static PrizeResponse from(RegisteredEvent.Prize prize) {
		return new PrizeResponse(prize.getId(), prize.getRank(), prize.getName(), prize.getDescription(),
				null, prize.getWinnerCount());
	}
}
