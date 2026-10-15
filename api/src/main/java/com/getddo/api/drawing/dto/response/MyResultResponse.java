package com.getddo.api.drawing.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.getddo.api.event.dto.response.PrizeResponse;
import com.getddo.core.drawing.domain.MyDrawResultStatus;
import com.getddo.core.drawing.domain.UserDrawResult;

/**
 * 본인 결과 조회의 공개 계약이다. 사용자 식별자나 내부 검토 정보는 포함하지 않는다.
 *
 * @param prize 공개 후 WON인 경우에만 제공하는 경품, 그 외에는 null
 * @param isPublished 실제 공개 기록의 존재 여부로, 응모 여부와는 별개
 * @param revision 실제 공개 버전, 공개 전에는 null
 */
public record MyResultResponse(UUID eventId, MyDrawResultStatus result, PrizeResponse prize,
		boolean isPublished, Integer revision, Instant serverTime) {
	/** 서비스가 판단한 결과를 기존 경품 응답 형식과 본인 결과 계약으로 변환한다. */
	public static MyResultResponse from(UserDrawResult.MyResult result) {
		return new MyResultResponse(result.getEventId(), result.getResult(),
				result.getPrize() == null ? null : PrizeResponse.from(result.getPrize()),
				result.isPublished(), result.getRevision(), result.getServerTime());
	}
}
