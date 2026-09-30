package com.getddo.api.user.dto.response;

import java.util.Locale;
import java.util.UUID;

import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

/** 내 정보 API에서 공개하는 7개 필드다. nullable 필드는 DB 값을 그대로 보존한다. */
public record UserProfileResponse(
		UUID id, String name, UserRole role, UserStatus status,
		String membership, String phoneNum, String email
) {
	public static UserProfileResponse from(User user) {
		String membership = null;
		if (user.membership() != null) {
			membership = user.membership().name().toLowerCase(Locale.ROOT);
		}

		return new UserProfileResponse(user.id(), user.name(), user.role(), user.status(),
				membership, user.phoneNum(), user.email());
	}
}
