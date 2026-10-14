package com.getddo.api.mission.dto.response;

import java.util.UUID;

import lombok.Builder;

/**
 * 문항의 선택지다. 정답 여부는 사용자 응답에 포함하지 않는다.
 */
@Builder
public record QuestionOptionResponse(UUID id, String optionText, int displayOrder) {
}
