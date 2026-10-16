package com.getddo.api.game.dto.response;

import lombok.Builder;

import com.getddo.core.game.domain.GamePlay;

/** 해당 플레이에 저장된 문제 생성 버전과 공개 seed. */
@Builder
public record GamePlayDataResponse(String generatorVersion, long seed) {

	public static GamePlayDataResponse from(GamePlay play) {
		return GamePlayDataResponse.builder().generatorVersion(play.getGeneratorVersion())
				.seed(play.getGenerationSeed()).build();
	}
}
