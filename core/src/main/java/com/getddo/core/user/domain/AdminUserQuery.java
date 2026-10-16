package com.getddo.core.user.domain;

import java.util.UUID;

import lombok.Getter;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.user.exception.UserErrorCode;
import com.getddo.core.user.exception.UserException;

/** 관리자 사용자 목록의 페이지와 AND 검색 조건을 검증한다. */
@Getter
public final class AdminUserQuery {
	private final PageQuery page;
	private final UUID userId;
	private final String keyword;
	private final UserRole role;
	private final UserStatus status;
	private final Membership membership;

	public AdminUserQuery(int page, int size, UUID userId, String keyword,
			UserRole role, UserStatus status, Membership membership) {
		String normalized = keyword == null ? null : keyword.strip();
		if (page < 1 || size < 1 || size > 100
				|| (normalized != null && normalized.codePointCount(0, normalized.length()) > 20)) {
			throw new UserException(UserErrorCode.INVALID_ADMIN_QUERY);
		}
		this.page = new PageQuery(page, size);
		this.userId = userId;
		this.keyword = normalized == null || normalized.isEmpty() ? null : normalized;
		this.role = role;
		this.status = status;
		this.membership = membership;
	}
}
