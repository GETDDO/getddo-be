package com.getddo.api.drawing.controller;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.drawing.dto.response.MyResultResponse;
import com.getddo.api.drawing.dto.response.PublicResultsResponse;
import com.getddo.core.drawing.service.UserDrawResultService;
import com.getddo.core.user.domain.User;

/** E08·E09는 공개 저장 기록을 기준으로 조회하며 발표를 실행하지 않는다. */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class UserDrawResultController {
	private final UserDrawResultService service;

	/** E08 공개 명단 조회를 위임하고, 서비스가 가린 개인정보만 응답 봉투에 담는다. */
	@GetMapping("/{eventId}/results")
	public ResponseEnvelope<PublicResultsResponse> results(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID eventId) {
		return ResponseEnvelope.success(PublicResultsResponse.from(service.publicResults(user, eventId)));
	}

	/** E09의 조회 대상을 요청 문맥의 본인으로 한정하며, 공개·당첨 판단은 서비스에 맡긴다. */
	@GetMapping("/{eventId}/my-result")
	public ResponseEnvelope<MyResultResponse> myResult(
			@CurrentUser(membershipRequired = false) User user, @PathVariable UUID eventId) {
		return ResponseEnvelope.success(MyResultResponse.from(service.myResult(user, eventId)));
	}
}
