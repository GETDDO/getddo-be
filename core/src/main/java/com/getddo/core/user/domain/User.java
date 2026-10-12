package com.getddo.core.user.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

/** 저장된 사용자 정보를 표현하는 불변 조회 모델이다. */
@Getter
public final class User {

	private final UUID id;
	private final String name;
	private final UserRole role;
	private final UserStatus status;
	private final Membership membership;
	private final String phoneNum;
	private final String email;
	private final Instant suspendedAt;
	private final String suspensionReason;
	private final Instant createdAt;
	private final Instant updatedAt;

	public User(UUID id, String name, UserRole role, UserStatus status, Membership membership,
			String phoneNum, String email, Instant suspendedAt, String suspensionReason,
			Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.name = name;
		this.role = role;
		this.status = status;
		this.membership = membership;
		this.phoneNum = phoneNum;
		this.email = email;
		this.suspendedAt = suspendedAt;
		this.suspensionReason = suspensionReason;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
}
