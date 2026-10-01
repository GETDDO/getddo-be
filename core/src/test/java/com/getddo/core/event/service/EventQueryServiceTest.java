package com.getddo.core.event.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.event.domain.EventQueryFilter;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.user.domain.Membership;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.exception.EventException;
import com.getddo.core.event.repository.EventQueryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventQueryServiceTest {
	private static final UUID ACTOR_ID = UUID.randomUUID();
	private static final EventQueryFilter EMPTY = new EventQueryFilter(null, null, null, null, null, null);
	private User user;
	private EventQueryRepository events;
	private EventQueryService service;

	@BeforeEach
	void setUp() {
		user = user(UserRole.USER, UserStatus.ACTIVE, Membership.VIP);
		events = mock(EventQueryRepository.class);
		service = new EventQueryService(events);
	}

	@Test
	@DisplayName("일반 사용자는 DB 멤버십으로 조회하며 관리자 목록에는 접근할 수 없다")
	void regularUserCanBrowseButCannotUseAdminQuery() {
		// given
		when(events.findAll(eq(EMPTY), any(), eq(true))).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when / then
		assertThat(service.findUserEvents(user, 1, 20, EMPTY).getItems()).isEmpty();
		verify(events).findAll(EMPTY, new PageQuery(1, 20), true);
		assertError(() -> service.findAdminEvents(user, 1, 20, EMPTY), EventErrorCode.ACCESS_DENIED);
	}

	@Test
	@DisplayName("사용자 문맥이 없거나 비활성 상태이면 이벤트 조회 전에 거절한다")
	void rejectsInvalidUserContextBeforeRepositoryQuery() {
		// given / when / then
		assertError(() -> service.findUserEvents(null, 1, 20, EMPTY), EventErrorCode.USER_CONTEXT_REQUIRED);
		assertError(() -> service.findUserEvent(null, UUID.randomUUID()), EventErrorCode.USER_CONTEXT_REQUIRED);
		user = user(UserRole.ADMIN, UserStatus.INACTIVE, null);
		assertError(() -> service.findUserEvents(user, 1, 20, EMPTY), EventErrorCode.ACCESS_DENIED);
		assertError(() -> service.findAdminEvent(user, UUID.randomUUID()), EventErrorCode.ACCESS_DENIED);
		verifyNoInteractions(events);
	}

	@Test
	@DisplayName("멤버십 없는 관리자는 사용자·관리자 목록을 모두 조회할 수 있다")
	void adminDoesNotRequireMembership() {
		// given
		user = user(UserRole.ADMIN, UserStatus.ACTIVE, null);
		when(events.findAll(eq(EMPTY), any(), eq(true))).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		when(events.findAll(eq(EMPTY), any(), eq(false))).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when / then
		assertThat(service.findUserEvents(user, 1, 20, EMPTY).getTotalElements()).isZero();
		assertThat(service.findAdminEvents(user, 1, 20, EMPTY).getTotalElements()).isZero();
	}

	@ParameterizedTest
	@CsvSource({"0,20", "1,0", "1,101", "-1,1"})
	@DisplayName("페이지와 크기 범위를 벗어나면 DB 조회 없이 400 오류를 낸다")
	void rejectsInvalidPagination(int page, int size) {
		// given / when / then
		assertError(() -> service.findUserEvents(user, page, size, EMPTY), EventErrorCode.INVALID_QUERY);
		verifyNoInteractions(events);
	}

	@Test
	@DisplayName("같거나 역전된 기간 구간은 거절한다")
	void rejectsEmptyOrReversedPeriod() {
		// given
		user = user(UserRole.ADMIN, UserStatus.ACTIVE, null);
		Instant from = Instant.parse("2026-10-10T00:00:00Z");
		// when / then
		for (Instant to : List.of(from, from.minusSeconds(1))) {
			assertError(() -> service.findAdminEvents(user, 1, 20,
					new EventQueryFilter(EventStatus.OPEN, null, null, null, from, to)), EventErrorCode.INVALID_QUERY);
		}
		verifyNoInteractions(events);
	}

	@ParameterizedTest
	@ValueSource(strings = {"0999-12-31T23:59:59Z", "9999-12-31T23:59:59.500000Z",
			"+10000-10-01T00:00:00Z", "+999999999-10-01T00:00:00Z"})
	@DisplayName("지원 범위 밖의 검색 시각은 단일 조건과 양쪽 조건 모두 DB 조회 전에 거절한다")
	void rejectsUnsupportedSearchTimes(String value) {
		// given
		user = user(UserRole.ADMIN, UserStatus.ACTIVE, null);
		Instant invalid = Instant.parse(value);
		Instant normal = Instant.parse("2099-10-10T00:00:00Z");
		List<EventQueryFilter> filters = List.of(
				new EventQueryFilter(null, null, null, null, invalid, null),
				new EventQueryFilter(null, null, null, null, null, invalid),
				new EventQueryFilter(null, null, null, null,
						invalid.isBefore(normal) ? invalid : normal, invalid.isBefore(normal) ? normal : invalid));
		// when / then
		for (EventQueryFilter filter : filters) {
			assertError(() -> service.findAdminEvents(user, 1, 20, filter), EventErrorCode.INVALID_QUERY);
		}
		verifyNoInteractions(events);
	}

	@Test
	@DisplayName("Instant의 최솟값과 최댓값도 DB 조회 없이 조회 조건 오류로 처리한다")
	void rejectsExtremeInstants() {
		// given
		user = user(UserRole.ADMIN, UserStatus.ACTIVE, null);
		// when / then
		for (Instant value : List.of(Instant.MIN, Instant.MAX)) {
			assertError(() -> service.findAdminEvents(user, 1, 20,
					new EventQueryFilter(null, null, null, null, value, null)), EventErrorCode.INVALID_QUERY);
			assertError(() -> service.findAdminEvents(user, 1, 20,
					new EventQueryFilter(null, null, null, null, null, value)), EventErrorCode.INVALID_QUERY);
		}
		verifyNoInteractions(events);
	}

	@Test
	@DisplayName("지원 범위의 경계 시각과 단일 조건·조건 없는 검색은 그대로 조회한다")
	void acceptsSupportedBoundariesAndOptionalBounds() {
		// given
		user = user(UserRole.ADMIN, UserStatus.ACTIVE, null);
		Instant from = Instant.parse("1000-01-01T00:00:00Z");
		Instant to = Instant.parse("9999-12-31T23:59:59.499999Z");
		when(events.findAll(any(), any(), eq(false))).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when / then
		for (EventQueryFilter filter : List.of(EMPTY,
				new EventQueryFilter(null, null, null, null, from, null),
				new EventQueryFilter(null, null, null, null, null, to),
				new EventQueryFilter(null, null, null, null, from, to))) {
			assertThat(service.findAdminEvents(user, 1, 20, filter).getTotalElements()).isZero();
			verify(events).findAll(filter, new PageQuery(1, 20), false);
		}
	}

	@Test
	@DisplayName("상세 조회에서 없는 이벤트는 404 업무 오류로 변환한다")
	void missingEventIsNotFound() {
		// given
		UUID id = UUID.randomUUID();
		when(events.findById(id)).thenReturn(Optional.empty());
		// when / then
		assertError(() -> service.findUserEvent(user, id), EventErrorCode.EVENT_NOT_FOUND);
	}

	private User user(UserRole role, UserStatus status, Membership membership) {
		return new User(ACTOR_ID, "조회 사용자", role, status, membership,
				null, null, null, null, Instant.EPOCH, Instant.EPOCH);
	}

	private void assertError(Runnable action, EventErrorCode error) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(EventException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(error));
	}
}
