package com.getddo.db.user.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.getddo.core.user.domain.Membership;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.db.common.entity.BaseUpdatableEntity;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseUpdatableEntity {

	@Column(nullable = false, length = 20)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserStatus status;

	@Enumerated(EnumType.STRING)
	private Membership membership;

	@Column(length = 11)
	private String phoneNum;

	private String email;

	private Instant suspendedAt;

	@Column(columnDefinition = "text")
	private String suspensionReason;
}
