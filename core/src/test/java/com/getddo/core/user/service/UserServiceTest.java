package com.getddo.core.user.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.exception.UserException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

	private static final UUID USER_ID = UUID.fromString("0199564e-45e0-7000-8000-0123456789ab");

	@ParameterizedTest
	@EnumSource(UserRole.class)
	@DisplayName("내 정보는 이미 조회한 활성 사용자를 재조회 없이 반환한다")
	void profileReusesActiveUser(UserRole role) {
		// given
		UserService service = new UserService(id -> {
			throw new AssertionError("내 정보 응답용으로 다시 조회하면 안 됩니다.");
		});
		User user = new User(USER_ID, "사용자", role, UserStatus.ACTIVE, null,
				null, null, null, null, null, null);

		// when / then
		assertThat(service.getProfile(user)).isSameAs(user);
	}

	@ParameterizedTest
	@EnumSource(UserRole.class)
	@DisplayName("일반 사용자와 관리자 모두 INACTIVE이면 내 정보 조회를 거절한다")
	void rejectsInactiveProfile(UserRole role) {
		// given
		UserService service = new UserService(id -> {
			throw new AssertionError("내 정보 상태 검증에서 다시 조회하면 안 됩니다.");
		});
		User user = new User(USER_ID, "사용자", role, UserStatus.INACTIVE, null,
				null, null, null, null, null, null);

		// when / then
		assertThatExceptionOfType(UserException.class)
				.isThrownBy(() -> service.getProfile(user))
				.satisfies(exception -> {
					assertThat(exception.getErrorCode().getStatus()).isEqualTo(403);
					assertThat(exception.getErrorCode().getCode()).isEqualTo("USER-007");
				});
	}

	@Test
	@DisplayName("ID가 null이면 DB 조회 전에 사용자 ID 필수 예외를 던진다")
	void rejectsNullIdBeforeQuery() {
		// given
		UserService service = new UserService(id -> {
			throw new AssertionError("null ID로 저장소를 호출하면 안 됩니다.");
		});

		// when / then
		assertThatExceptionOfType(UserException.class)
				.isThrownBy(() -> service.findById(null))
				.satisfies(exception -> {
					assertThat(exception.getErrorCode().getCode()).isEqualTo("USER-001");
					assertThat(exception.getErrorCode().getStatus()).isEqualTo(400);
				});
	}

	@Test
	@DisplayName("미등록 ID이면 기존 사용자 확인 실패 계약의 업무 예외를 던진다")
	void rejectsMissingUser() {
		// given
		UserService service = new UserService(id -> Optional.empty());

		// when / then
		assertThatExceptionOfType(UserException.class)
				.isThrownBy(() -> service.findById(USER_ID))
				.satisfies(exception -> {
					assertThat(exception.getErrorCode().getCode()).isEqualTo("USER-002");
					assertThat(exception.getErrorCode().getStatus()).isEqualTo(401);
				});
	}

	@Test
	@DisplayName("저장소 장애를 미등록 사용자 예외로 바꾸지 않고 그대로 전달한다")
	void propagatesRepositoryFailure() {
		// given
		RuntimeException failure = new IllegalStateException("조회 실패");
		UserService service = new UserService(id -> {
			throw failure;
		});

		// when / then
		assertThatThrownBy(() -> service.findById(USER_ID)).isSameAs(failure);
	}
}
