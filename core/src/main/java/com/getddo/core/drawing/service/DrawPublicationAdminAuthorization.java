package com.getddo.core.drawing.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.user.repository.UserRepository;

import static com.getddo.core.drawing.exception.DrawPublicationErrorCode.ADMIN_REQUIRED;

/** 확인·이력 서비스가 실행·취소와 같은 활성 관리자 조건을 사용한다. */
@RequiredArgsConstructor
public final class DrawPublicationAdminAuthorization {
	private final DrawAdminAuthorization admins;

	public DrawPublicationAdminAuthorization(UserRepository users) { this(new DrawAdminAuthorization(users)); }

	/** 호출한 서비스의 트랜잭션에서 DB 사용자 역할·상태를 대조한다. */
	public void requireActiveAdmin(UUID actorId) {
		admins.requireActiveAdmin(actorId, ADMIN_REQUIRED);
	}
}
