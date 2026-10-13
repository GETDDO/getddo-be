package com.getddo.core.drawing.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.repository.UserRepository;

import static com.getddo.core.drawing.exception.DrawPublicationErrorCode.ADMIN_REQUIRED;

/** GD-139 확인·이력 서비스가 같은 활성 관리자 조건을 사용한다. 실행 권한/자동 실행 계약은 GD-87 소유다. */
@RequiredArgsConstructor
public final class DrawPublicationAdminAuthorization {
	private final UserRepository users;

	/** 호출한 서비스의 트랜잭션에서 DB 사용자 역할·상태를 대조한다. */
	public void requireActiveAdmin(UUID actorId) {
		if (actorId == null) throw new BusinessException(ADMIN_REQUIRED);
		User actor = users.findById(actorId).orElseThrow(() -> new BusinessException(ADMIN_REQUIRED));
		if (actor.getRole() != UserRole.ADMIN || actor.getStatus() != UserStatus.ACTIVE)
			throw new BusinessException(ADMIN_REQUIRED);
	}
}
