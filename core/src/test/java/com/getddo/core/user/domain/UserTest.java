package com.getddo.core.user.domain;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UserTest {

	private static final UUID USER_ID = UUID.fromString("0199564e-45e0-7000-8000-0123456789ab");

	@ParameterizedTest(name = "{0}가 null이면 생성할 수 없다")
	@MethodSource("missingRequiredFields")
	@DisplayName("사용자 ID, 이름, 역할, 상태는 필수다")
	void rejectsMissingRequiredField(String field, UUID id, String name, UserRole role, UserStatus status) {
		// when / then
		assertThatNullPointerException().isThrownBy(() -> new User(id, name, role, status, null,
				null, null, null, null, null, null));
	}

	private static Stream<Arguments> missingRequiredFields() {
		return Stream.of(
				Arguments.of("id", null, "사용자", UserRole.USER, UserStatus.ACTIVE),
				Arguments.of("name", USER_ID, null, UserRole.USER, UserStatus.ACTIVE),
				Arguments.of("role", USER_ID, "사용자", null, UserStatus.ACTIVE),
				Arguments.of("status", USER_ID, "사용자", UserRole.USER, null));
	}

	@ParameterizedTest
	@EnumSource(UserRole.class)
	@DisplayName("멤버십과 부가 정보가 없는 사용자도 생성할 수 있다")
	void acceptsMissingOptionalFields(UserRole role) {
		// when / then
		assertThatCode(() -> new User(USER_ID, "사용자", role, UserStatus.ACTIVE, null,
				null, null, null, null, null, null)).doesNotThrowAnyException();
	}
}
