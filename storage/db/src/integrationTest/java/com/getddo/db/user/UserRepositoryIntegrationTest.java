package com.getddo.db.user;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.getddo.core.user.exception.UserException;
import com.getddo.core.user.domain.Membership;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.exception.UserErrorCode;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;
import com.getddo.db.support.MySqlTestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest(properties = {
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
		"spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = UserPersistenceTestConfiguration.class)
@ActiveProfiles("test")
@Import(MySqlTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserRepositoryIntegrationTest {

	private static final UUID USER_ID = UUID.fromString("0199564e-45e0-7000-8000-0123456789ab");
	private static final Instant CREATED = Instant.parse("2026-09-29T14:59:59.123456Z");
	private static final Instant UPDATED = Instant.parse("2026-09-30T15:00:01.234567Z");
	private static final Instant SUSPENDED = Instant.parse("2026-09-30T15:00:00.345678Z");

	@Autowired
	private UserService userService;
	@Autowired
	private JdbcTemplate jdbc;

	@ParameterizedTest
	@CsvSource({"USER, ACTIVE", "USER, INACTIVE", "ADMIN, ACTIVE", "ADMIN, INACTIVE"})
	@DisplayName("기존 BINARY(16) UUID로 USER와 ADMIN을 조회하고 nullable 값을 보존한다")
	void loadsExistingUuidAndNullableFields(UserRole role, UserStatus status) {
		// given
		jdbc.update("""
				insert into users (id, name, role, status, created_at, updated_at)
				values (unhex(replace(?, '-', '')), '테스트 사용자', ?, ?,
				        '2026-09-29 14:59:59.123456', '2026-09-30 15:00:01.234567')
				""", USER_ID.toString(), role.name(), status.name());

		// when / then
		assertThat(userService.findById(USER_ID)).isEqualTo(new User(
				USER_ID, "테스트 사용자", role, status, null,
				null, null, null, null, CREATED, UPDATED));
	}

	@ParameterizedTest
	@CsvSource({"EXCELLENT, excellent, EXCELLENT", "VIP, vip, VIP", "VVIP, vvip, VVIP"})
	@DisplayName("멤버십은 대문자로 저장·조회하고 기존 소문자 비교 조건도 유지한다")
	void mapsEveryMembershipCode(String storedCode, String lowercaseCode, Membership expected) {
		// given
		jdbc.update("""
				insert into users (id, name, role, status, membership, created_at, updated_at)
				values (unhex(replace(?, '-', '')), '테스트 사용자', 'USER', 'ACTIVE', ?,
				        '2026-09-29 14:59:59.123456', '2026-09-30 15:00:01.234567')
				""", USER_ID.toString(), storedCode);

		// when / then
		assertThat(jdbc.queryForObject("select membership from users where id = unhex(replace(?, '-', ''))",
				String.class, USER_ID.toString())).isEqualTo(storedCode);
		assertThat(userService.findById(USER_ID).membership()).isEqualTo(expected);
		assertThat(jdbc.queryForObject("""
				select count(*) from users where id = unhex(replace(?, '-', '')) and membership = ?
				""", Long.class, USER_ID.toString(), lowercaseCode)).isEqualTo(1L);
	}

	@ParameterizedTest
	@CsvSource({"USER, ACTIVE", "USER, INACTIVE", "ADMIN, ACTIVE", "ADMIN, INACTIVE"})
	@DisplayName("역할과 상태를 바꾸지 않고 연락처 및 UTC 시각의 마이크로초를 보존한다")
	void preservesRoleStatusAndUtcTimes(UserRole role, UserStatus status) {
		// given
		jdbc.update("""
				insert into users (id, name, role, status, membership, phone_num, email,
				                   suspended_at, suspension_reason, created_at, updated_at)
				values (unhex(replace(?, '-', '')), '테스트 사용자', ?, ?, 'VIP', '01000000000',
				        'user@example.test', '2026-09-30 15:00:00.345678', '테스트 정지 사유',
				        '2026-09-29 14:59:59.123456', '2026-09-30 15:00:01.234567')
				""", USER_ID.toString(), role.name(), status.name());

		// when / then
		assertThat(userService.findById(USER_ID)).isEqualTo(new User(
				USER_ID, "테스트 사용자", role, status, Membership.VIP,
				"01000000000", "user@example.test", SUSPENDED, "테스트 정지 사유", CREATED, UPDATED));
	}

	@Test
	@DisplayName("등록되지 않은 ID는 Service에서 미등록 사용자 예외로 처리한다")
	void missingIdThrowsBusinessException() {
		// when / then
		assertThatExceptionOfType(UserException.class)
				.isThrownBy(() -> userService.findById(USER_ID))
				.satisfies(exception -> assertThat(exception.getErrorCode())
						.isEqualTo(UserErrorCode.USER_NOT_FOUND));
	}
}
