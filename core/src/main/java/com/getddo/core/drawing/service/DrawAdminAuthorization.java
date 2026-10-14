package com.getddo.core.drawing.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.exception.ErrorCode;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.repository.UserRepository;

/** 추첨 관리 기능의 활성 관리자 조건을 공유하고 각 기능의 오류 계약을 유지한다. */
@Service
@RequiredArgsConstructor
public class DrawAdminAuthorization {
	private final UserRepository users;

	/** 호출 서비스의 트랜잭션 안에서 현재 사용자를 조회한다. */
	public void requireActiveAdmin(UUID actorId, ErrorCode denied) {
		if (actorId == null) throw new BusinessException(denied);
		validate(users.findById(actorId).orElse(null), denied);
	}

	/** 공통 사용자 문맥이 이미 조회한 사용자를 같은 조건으로 검증한다. */
	public static void validate(User user, ErrorCode denied) {
		if (user == null || user.getId() == null || user.getRole() != UserRole.ADMIN || user.getStatus() != UserStatus.ACTIVE)
			throw new BusinessException(denied);
	}
}
