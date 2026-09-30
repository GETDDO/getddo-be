package com.getddo.core.user.domain;

import java.time.Instant;
import java.util.UUID;

/** 저장된 사용자 정보를 표현하는 불변 조회 모델이다. */
public record User(
		UUID id,
		String name,
		UserRole role,
		UserStatus status,
		Membership membership,
		String phoneNum,
		String email,
		Instant suspendedAt,
		String suspensionReason,
		Instant createdAt,
		Instant updatedAt
) {
}
