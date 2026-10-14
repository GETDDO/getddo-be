package com.getddo.core.notification.service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.notification.domain.Notification;
import com.getddo.core.notification.domain.NotificationErrorCode;
import com.getddo.core.notification.repository.NotificationRepository;
import com.getddo.core.user.domain.Membership;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
	private static final UUID USER_ID = UUID.randomUUID();
	private static final User USER = new User(USER_ID, "사용자", UserRole.USER, UserStatus.ACTIVE,
			Membership.VIP, null, null, null, null, null, null);
	private static final UUID OTHER_NOTIFICATION = UUID.randomUUID();

	@Mock private NotificationRepository repository;
	private NotificationService service;

	@BeforeEach
	void setUp() {
		service = new NotificationService(repository);
	}

	@Test
	@DisplayName("공통 계층에서 확인한 사용자로 알림 목록과 조회 조건만 저장소에 전달한다")
	void readsNotificationsForVerifiedUser() {
		// given
		CursorResult<Notification> result = new CursorResult<>(List.of(), null, 0);
		when(repository.findMine(USER_ID, "cursor", 20, false)).thenReturn(result);

		// when / then
		assertThat(service.findMine(USER, "cursor", 20, false)).isSameAs(result);
		verify(repository).findMine(USER_ID, "cursor", 20, false);
		verifyNoMoreInteractions(repository);
	}

	@Test
	@DisplayName("조회 크기가 100을 초과하면 거절한다")
	void rejectsInvalidPageSize() {
		// given: 공통 사용자 처리에서 확인한 사용자다.

		// when / then
		assertThatThrownBy(() -> service.findMine(USER, null, 101, null))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(NotificationErrorCode.INVALID_QUERY);
	}

	@Test
	@DisplayName("타인의 알림은 읽음 처리할 수 없다")
	void rejectsAnotherUsersNotification() {
		// given: 공통 사용자 처리에서 확인한 사용자다.

		// when / then
		assertThatThrownBy(() -> service.markRead(USER, OTHER_NOTIFICATION))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(NotificationErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("본인 알림만 읽음 처리한다")
	void marksOnlyTheOwnersNotification() {
		// given
		when(repository.markRead(OTHER_NOTIFICATION, USER_ID)).thenReturn(1);

		// when / then
		assertThat(service.markRead(USER, OTHER_NOTIFICATION)).isEqualTo(OTHER_NOTIFICATION);
		verify(repository).markRead(OTHER_NOTIFICATION, USER_ID);
	}

	@Test
	@DisplayName("이미 읽은 본인 알림의 재요청도 성공한다")
	void alreadyReadNotificationRemainsSuccessful() {
		// given
		when(repository.belongsTo(OTHER_NOTIFICATION, USER_ID)).thenReturn(true);

		// when / then
		assertThat(service.markRead(USER, OTHER_NOTIFICATION)).isEqualTo(OTHER_NOTIFICATION);
	}

	@Test
	@DisplayName("전체 읽음은 실제 변경된 미읽음 건수를 반환한다")
	void returnsChangedUnreadCount() {
		// given
		when(repository.markAllRead(USER_ID)).thenReturn(2L);

		// when / then
		assertThat(service.markAllRead(USER)).isEqualTo(2);
		verify(repository).markAllRead(USER_ID);
	}

	@ParameterizedTest
	@EnumSource(UserRole.class)
	@DisplayName("내 정보 API의 비활성 제한을 사용자·관리자의 알림 조회에 적용하지 않는다")
	void inactiveUserCanReadNotifications(UserRole role) {
		// given
		User user = new User(USER_ID, "사용자", role, UserStatus.INACTIVE,
				Membership.VIP, null, null, null, null, null, null);
		CursorResult<Notification> result = new CursorResult<>(List.of(), null, 0);
		when(repository.findMine(USER_ID, null, 20, null)).thenReturn(result);

		// when / then
		assertThat(service.findMine(user, null, 20, null)).isSameAs(result);
	}
}
