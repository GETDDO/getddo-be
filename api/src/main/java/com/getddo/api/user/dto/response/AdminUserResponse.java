package com.getddo.api.user.dto.response;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import lombok.Builder;

import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

/** 관리자 조회에서 공개하는 운영 정보만 담고 연락처·정지 정보는 노출하지 않는다. */
@Builder
public record AdminUserResponse(UUID id, String name, UserRole role, UserStatus status,
		String membership, Instant createdAt, Instant updatedAt) {
	public static AdminUserResponse from(User user) {
		return AdminUserResponse.builder()
				.id(user.getId()).name(user.getName()).role(user.getRole()).status(user.getStatus())
				.membership(user.getMembership() == null ? null : user.getMembership().name().toLowerCase(Locale.ROOT))
				.createdAt(user.getCreatedAt()).updatedAt(user.getUpdatedAt()).build();
	}
}
