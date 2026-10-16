package com.getddo.api.game.controller;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.game.dto.response.MyGameStatisticsResponse;
import com.getddo.core.game.service.GameStatisticsService;
import com.getddo.core.user.domain.User;

/** 본인의 게임 통계 조회(G05). 사용자를 요청 파라미터로 받지 않는다. */
@RestController
@RequestMapping("/api/v1/games/{gameId}/statistics/me")
@RequiredArgsConstructor
public class GameStatisticsController {
	private final GameStatisticsService service;

	@GetMapping
	public ResponseEnvelope<MyGameStatisticsResponse> find(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID gameId) {
		return ResponseEnvelope.success(MyGameStatisticsResponse.from(service.find(user, gameId)));
	}
}
