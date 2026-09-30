package com.getddo.api.user;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.api.common.context.CurrentUserArgumentResolver;
import com.getddo.api.common.exception.GlobalExceptionHandler;
import com.getddo.api.user.controller.UserController;
import com.getddo.core.user.domain.Membership;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000521");
	private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000522");
	private final Map<UUID, User> users = new ConcurrentHashMap<>();
	private final AtomicInteger lookups = new AtomicInteger();
	private RuntimeException repositoryFailure;
	private CyclicBarrier lookupBarrier;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		users.put(USER_ID, user(USER_ID, UserRole.USER, UserStatus.ACTIVE, Membership.VIP));
		users.put(ADMIN_ID, user(ADMIN_ID, UserRole.ADMIN, UserStatus.ACTIVE, null));
		UserService service = new UserService(id -> {
			lookups.incrementAndGet();
			if (repositoryFailure != null) {
				throw repositoryFailure;
			}
			if (lookupBarrier != null) {
				try {
					lookupBarrier.await(5, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new AssertionError("동시 요청이 저장소에 도달하지 않았습니다.", exception);
				}
			}
			return Optional.ofNullable(users.get(id));
		});
		mvc = MockMvcBuilders.standaloneSetup(new UserController(service), new ContextController())
				.setCustomArgumentResolvers(new CurrentUserArgumentResolver(service))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@ParameterizedTest
	@MethodSource("memberships")
	@DisplayName("일반 사용자 응답은 DB의 공개 7개 필드와 소문자 멤버십을 반환하고 한 번만 조회한다")
	void returnsUserProfile(Membership membership, String code) throws Exception {
		// given
		users.put(USER_ID, user(USER_ID, UserRole.USER, UserStatus.ACTIVE, membership));

		// when / then
		mvc.perform(request(USER_ID.toString(), "USER", code))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.data", aMapWithSize(7)))
				.andExpect(jsonPath("$.data.id").value(USER_ID.toString()))
				.andExpect(jsonPath("$.data.name").value("DB 사용자"))
				.andExpect(jsonPath("$.data.role").value("USER"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.membership").value(code))
				.andExpect(jsonPath("$.data.phoneNum").value("01001234567"))
				.andExpect(jsonPath("$.data.email").value("user@example.test"));
		assertThat(lookups.get()).isEqualTo(1);
	}

	static Stream<Arguments> memberships() {
		return Stream.of(Arguments.of(Membership.EXCELLENT, "excellent"),
				Arguments.of(Membership.VIP, "vip"), Arguments.of(Membership.VVIP, "vvip"));
	}

	@ParameterizedTest
	@EnumSource(value = Membership.class, names = {"VIP", "VVIP"})
	@DisplayName("관리자는 멤버십 헤더 없이도 조회하며 DB 멤버십을 보존한다")
	void adminDoesNotRequireMembership(Membership membership) throws Exception {
		// given
		users.put(ADMIN_ID, user(ADMIN_ID, UserRole.ADMIN, UserStatus.ACTIVE, membership));
		String expected = membership == Membership.VIP ? "vip" : "vvip";

		// when / then
		mvc.perform(request(ADMIN_ID.toString(), "ADMIN", null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", aMapWithSize(7)))
				.andExpect(jsonPath("$.data.id").value(ADMIN_ID.toString()))
				.andExpect(jsonPath("$.data.role").value("ADMIN"))
				.andExpect(jsonPath("$.data.membership").value(expected));
	}

	@Test
	@DisplayName("관리자의 nullable 필드는 응답에 존재하며 null을 유지한다")
	void preservesNullFields() throws Exception {
		// given
		users.put(ADMIN_ID, new User(ADMIN_ID, "관리자", UserRole.ADMIN, UserStatus.ACTIVE,
				null, null, null, null, null, Instant.EPOCH, Instant.EPOCH));

		// when / then
		mvc.perform(request(ADMIN_ID.toString(), "ADMIN", null))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", aMapWithSize(7)))
				.andExpect(jsonPath("$.data", hasKey("membership")))
				.andExpect(jsonPath("$.data", hasKey("phoneNum")))
				.andExpect(jsonPath("$.data", hasKey("email")))
				.andExpect(jsonPath("$.data.membership").value(nullValue()))
				.andExpect(jsonPath("$.data.phoneNum").value(nullValue()))
				.andExpect(jsonPath("$.data.email").value(nullValue()));
	}

	@ParameterizedTest
	@MethodSource("missingHeaders")
	@DisplayName("일반 사용자의 필수 헤더 누락은 조회 전에 401로 응답한다")
	void rejectsMissingHeaders(String id, String role, String membership) throws Exception {
		// given / when / then
		mvc.perform(request(id, role, membership))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-003"))
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data").value(nullValue()));
		assertThat(lookups.get()).isZero();
	}

	static Stream<Arguments> missingHeaders() {
		return Stream.of(Arguments.of(null, "USER", "vip"),
				Arguments.of(USER_ID.toString(), null, "vip"),
				Arguments.of(USER_ID.toString(), "USER", null));
	}

	@ParameterizedTest
	@MethodSource("malformedHeaders")
	@DisplayName("잘못된 UUID·역할·멤버십은 조회 전에 400으로 응답한다")
	void rejectsMalformedHeaders(String id, String role, String membership) throws Exception {
		// given / when / then
		mvc.perform(request(id, role, membership))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
		assertThat(lookups.get()).isZero();
	}

	static Stream<Arguments> malformedHeaders() {
		return Stream.of(Arguments.of("bad", "USER", "vip"), Arguments.of("1-1-1-1-1", "USER", "vip"),
				Arguments.of("", "USER", "vip"), Arguments.of(" " + USER_ID, "USER", "vip"),
				Arguments.of(USER_ID.toString(), "user", "vip"), Arguments.of(USER_ID.toString(), "", "vip"),
				Arguments.of(USER_ID.toString(), "OWNER", "vip"), Arguments.of(USER_ID.toString(), "USER", "VIP"),
				Arguments.of(USER_ID.toString(), "USER", ""), Arguments.of(USER_ID.toString(), "USER", "gold"),
				Arguments.of(ADMIN_ID.toString(), "ADMIN", "gold"));
	}

	@ParameterizedTest
	@ValueSource(strings = {"X-User-ID", "X-User-Role", "X-User-Membership"})
	@DisplayName("중복된 사용자 헤더를 임의로 선택하지 않고 400으로 거절한다")
	void rejectsDuplicateHeaders(String header) throws Exception {
		// given / when / then
		mvc.perform(request(USER_ID.toString(), "USER", "vip").header(header, "extra"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
		assertThat(lookups.get()).isZero();
	}

	@Test
	@DisplayName("미등록 사용자는 기존 Service 오류로 401을 반환한다")
	void rejectsUnknownUser() throws Exception {
		// given
		users.remove(USER_ID);
		// when / then
		mvc.perform(request(USER_ID.toString(), "USER", "vip"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER-002"));
	}

	@Test
	@DisplayName("헤더 역할이 DB 역할과 다르면 403으로 응답한다")
	void rejectsRoleMismatch() throws Exception {
		// given / when / then
		mvc.perform(request(USER_ID.toString(), "ADMIN", null))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER-004"));
	}

	@Test
	@DisplayName("일반 사용자의 DB 멤버십이 없으면 헤더로 보충하지 않고 403으로 응답한다")
	void rejectsMissingDatabaseMembership() throws Exception {
		// given
		users.put(USER_ID, user(USER_ID, UserRole.USER, UserStatus.ACTIVE, null));
		// when / then
		mvc.perform(request(USER_ID.toString(), "USER", "vip"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER-005"));
	}

	@Test
	@DisplayName("일반 사용자의 멤버십 불일치는 DB를 변경하지 않고 409로 응답한다")
	void rejectsMembershipMismatch() throws Exception {
		// given / when / then
		mvc.perform(request(USER_ID.toString(), "USER", "vvip"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER-006"));
		assertThat(users.get(USER_ID).membership()).isEqualTo(Membership.VIP);
	}

	@Test
	@DisplayName("관리자의 추가 멤버십 헤더는 DB 응답 값을 덮어쓰지 않는다")
	void adminMembershipComesFromDatabase() throws Exception {
		// given / when / then
		mvc.perform(request(ADMIN_ID.toString(), "ADMIN", "vvip"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.membership").value(nullValue()));
	}

	@ParameterizedTest
	@EnumSource(UserRole.class)
	@DisplayName("INACTIVE의 내 정보 조회는 역할에 관계없이 403으로 응답한다")
	void rejectsInactiveProfile(UserRole role) throws Exception {
		// given
		users.put(USER_ID, user(USER_ID, role, UserStatus.INACTIVE, Membership.VIP));
		// when / then
		mvc.perform(request(USER_ID.toString(), role.name(), "vip"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER-007"));
	}

	@Test
	@DisplayName("DB 장애는 미등록 사용자로 바꾸지 않고 상세 정보 없는 500으로 응답한다")
	void databaseFailureReturnsServerError() throws Exception {
		// given
		repositoryFailure = new IllegalStateException("internal database detail");
		// when / then
		String body = mvc.perform(request(USER_ID.toString(), "USER", "vip"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("COMMON-001"))
				.andExpect(jsonPath("$.data").value(nullValue()))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("internal database detail");
	}

	@Test
	@DisplayName("사용자 정보가 필요 없는 API는 헤더 누락·오류와 DB 장애의 영향을 받지 않는다")
	void unrelatedEndpointNeverLoadsUser() throws Exception {
		// given
		repositoryFailure = new IllegalStateException("DB를 호출하면 안 됩니다.");
		// when / then
		mvc.perform(get("/test/plain")).andExpect(status().isOk());
		mvc.perform(get("/test/plain").header("X-User-ID", "bad"))
				.andExpect(status().isOk());
		assertThat(lookups.get()).isZero();
	}

	@Test
	@DisplayName("공통 resolver는 INACTIVE 상태를 보존하고 U01의 제한을 다른 API에 강제하지 않는다")
	void resolverPreservesInactiveState() throws Exception {
		// given
		users.put(USER_ID, user(USER_ID, UserRole.USER, UserStatus.INACTIVE, Membership.VIP));
		// when / then
		mvc.perform(get("/test/context").header("X-User-ID", USER_ID)
				.header("X-User-Role", "USER").header("X-User-Membership", "vip"))
				.andExpect(status().isOk()).andExpect(content().string("INACTIVE"));
	}

	@Test
	@DisplayName("한 요청에서 사용자 파라미터가 여러 개여도 DB 조회는 한 번이다")
	void reusesUserWithinRequest() throws Exception {
		// given / when / then
		mvc.perform(get("/test/twice").header("X-User-ID", USER_ID)
				.header("X-User-Role", "USER").header("X-User-Membership", "vip"))
				.andExpect(status().isOk()).andExpect(content().string("true"));
		assertThat(lookups.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("연속 요청은 사용자와 변경된 DB 정보를 매번 새로 조회한다")
	void sequentialRequestsUseFreshUsers() throws Exception {
		// given / when / then
		mvc.perform(request(USER_ID.toString(), "USER", "vip"))
				.andExpect(jsonPath("$.data.id").value(USER_ID.toString()));
		mvc.perform(request(ADMIN_ID.toString(), "ADMIN", null))
				.andExpect(jsonPath("$.data.id").value(ADMIN_ID.toString()));
		users.put(USER_ID, user(USER_ID, UserRole.USER, UserStatus.ACTIVE, Membership.VVIP));
		mvc.perform(request(USER_ID.toString(), "USER", "vvip"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.membership").value("vvip"));
		assertThat(lookups.get()).isEqualTo(3);
	}

	@Test
	@DisplayName("동시에 진행되는 요청 사이에 사용자 정보가 섞이지 않는다")
	void concurrentRequestsKeepUsersSeparate() throws Exception {
		// given: 두 요청이 모두 저장소에 도달한 뒤 함께 진행한다.
		lookupBarrier = new CyclicBarrier(2);
		try (var executor = Executors.newFixedThreadPool(2)) {
			// when
			var userResult = executor.submit(() -> mvc.perform(request(USER_ID.toString(), "USER", "vip"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(USER_ID.toString())));
			var adminResult = executor.submit(() -> mvc.perform(request(ADMIN_ID.toString(), "ADMIN", null))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(ADMIN_ID.toString())));
			// then
			userResult.get(10, TimeUnit.SECONDS);
			adminResult.get(10, TimeUnit.SECONDS);
			assertThat(lookups.get()).isEqualTo(2);
		}
	}

	private static MockHttpServletRequestBuilder request(String id, String role, String membership) {
		MockHttpServletRequestBuilder request = get("/api/v1/users/me");
		if (id != null) request.header("X-User-ID", id);
		if (role != null) request.header("X-User-Role", role);
		if (membership != null) request.header("X-User-Membership", membership);
		return request;
	}

	private static User user(UUID id, UserRole role, UserStatus status, Membership membership) {
		return new User(id, "DB 사용자", role, status, membership, "01001234567", "user@example.test",
				Instant.EPOCH, "응답에 노출하지 않을 사유", Instant.EPOCH, Instant.EPOCH);
	}

	@RestController
	static class ContextController {
		@GetMapping("/test/plain")
		String plain() { return "ok"; }

		@GetMapping("/test/context")
		String context(@CurrentUser User user) { return user.status().name(); }

		@GetMapping("/test/twice")
		String twice(@CurrentUser User first, @CurrentUser User second) {
			return Boolean.toString(first == second);
		}
	}
}
