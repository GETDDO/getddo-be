package com.getddo.api.game.controller;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.game.dto.response.GamePlayStartResponse;
import com.getddo.core.game.domain.GamePlayStartResult;
import com.getddo.core.game.service.GamePlayStartService;
import com.getddo.core.user.domain.User;

/** 게임 시작(G03). 사용자 헤더는 공통 사용자 문맥 처리로 DB와 대조한다. */
@RestController
@RequestMapping("/api/v1/games/{gameId}/plays")
@RequiredArgsConstructor
public class GamePlayController {

	private final GamePlayStartService service;

	/** 시작 버튼에서 호출하며 저장이 성공한 새 플레이의 정보만 201로 반환한다. */
	@PostMapping
	public ResponseEntity<ResponseEnvelope<GamePlayStartResponse>> start(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID gameId) {
		GamePlayStartResult result = service.start(user, gameId);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ResponseEnvelope.success(GamePlayStartResponse.from(result)));
	}
}
