package com.getddo.api.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.api.user.dto.response.UserProfileResponse;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.service.UserService;

/** 시연용으로 선택한 사용자 자신의 정보를 제공한다. */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping("/me")
	public ResponseEnvelope<UserProfileResponse> getProfile(@CurrentUser User user) {
		return ResponseEnvelope.success(UserProfileResponse.from(userService.getProfile(user)));
	}
}
