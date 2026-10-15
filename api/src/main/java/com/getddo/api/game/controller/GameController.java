package com.getddo.api.game.controller;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.game.dto.response.GameDetailResponse;
import com.getddo.api.game.dto.response.GameSummaryResponse;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.game.service.GameQueryService;
import com.getddo.core.user.domain.User;

/** 게임 목록(G01)·상세(G02). 공통 사용자 문맥으로 USER/ADMIN 헤더와 DB 정보를 대조한다. */
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

	private final GameQueryService queries;
	private final TimeProvider timeProvider;

	/** 멤버십 제한 없이 활성 게임 전체를 제공한다. 빈 목록은 200과 빈 배열이다. */
	@GetMapping
	public ResponseEnvelope<List<GameSummaryResponse>> list(
			@CurrentUser(membershipRequired = false) User user) {
		return ResponseEnvelope.success(queries.findGames().stream().map(GameSummaryResponse::from).toList());
	}

	/** 선택 게임의 공개 규칙과 공통 시계의 UTC 시각을 제공한다. 조회는 플레이를 시작하지 않는다. */
	@GetMapping("/{gameId}")
	public ResponseEnvelope<GameDetailResponse> detail(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID gameId) {
		return ResponseEnvelope.success(GameDetailResponse.from(queries.findGame(gameId), timeProvider.now()));
	}
}
