package com.getddo.api.user.dto.request;

import java.util.UUID;

import lombok.Getter;

import com.getddo.api.common.exception.CommonErrorCode;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.user.domain.AdminUserQuery;
import com.getddo.core.user.domain.Membership;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

/** 쿼리 타입 해석은 MVC에 맡기고 소문자 멤버십 코드를 도메인 값으로 변환한다. */
@Getter
public final class AdminUserQueryRequest {
	private final UUID userId;
	private final String keyword;
	private final UserRole role;
	private final UserStatus status;
	private final String membership;

	public AdminUserQueryRequest(UUID userId, String keyword, UserRole role, UserStatus status, String membership) {
		this.userId = userId;
		this.keyword = keyword;
		this.role = role;
		this.status = status;
		this.membership = membership;
	}

	public AdminUserQuery toDomain(int page, int size) {
		Membership grade = membership == null ? null : switch (membership) {
			case "excellent" -> Membership.EXCELLENT;
			case "vip" -> Membership.VIP;
			case "vvip" -> Membership.VVIP;
			default -> throw new BusinessException(CommonErrorCode.INVALID_FORMAT);
		};
		return new AdminUserQuery(page, size, userId, keyword, role, status, grade);
	}
}
