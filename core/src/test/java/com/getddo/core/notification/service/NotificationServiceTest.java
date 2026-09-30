package com.getddo.core.notification.service;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.notification.domain.NotificationErrorCode;
import com.getddo.core.notification.repository.NotificationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
	private static final UUID USER = UUID.randomUUID();
	private static final UUID OTHER_NOTIFICATION = UUID.randomUUID();

	@Mock private NotificationRepository repository;
	private NotificationService service;

	@BeforeEach
	void setUp() {
		service = new NotificationService(repository);
	}

	@Test
	@DisplayName("등록되지 않은 사용자로 알림을 조회하면 거절한다")
	void rejectsUnknownUserBeforeReadingNotifications() {
		// given: Mock 저장소는 등록 사용자 없음을 반환한다.

		// when / then
		assertThatThrownBy(() -> service.findMine(USER, null, null, 20, null))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(NotificationErrorCode.USER_CONTEXT_INVALID);
		verify(repository).userExists(USER);
	}

	@Test
	@DisplayName("조회 크기가 100을 초과하면 거절한다")
	void rejectsInvalidPageSize() {
		// given
		when(repository.userExists(USER)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> service.findMine(USER, null, null, 101, null))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(NotificationErrorCode.INVALID_QUERY);
	}

	@Test
	@DisplayName("타인의 알림은 읽음 처리할 수 없다")
	void rejectsAnotherUsersNotification() {
		// given
		when(repository.userExists(USER)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> service.markRead(USER, null, OTHER_NOTIFICATION))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(NotificationErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("본인 알림만 읽음 처리한다")
	void marksOnlyTheOwnersNotification() {
		// given
		when(repository.userExists(USER)).thenReturn(true);
		when(repository.markRead(OTHER_NOTIFICATION, USER)).thenReturn(1);

		// when / then
		assertThat(service.markRead(USER, null, OTHER_NOTIFICATION)).isEqualTo(OTHER_NOTIFICATION);
		verify(repository).markRead(OTHER_NOTIFICATION, USER);
	}

	@Test
	@DisplayName("이미 읽은 본인 알림의 재요청도 성공한다")
	void alreadyReadNotificationRemainsSuccessful() {
		// given
		when(repository.userExists(USER)).thenReturn(true);
		when(repository.belongsTo(OTHER_NOTIFICATION, USER)).thenReturn(true);

		// when / then
		assertThat(service.markRead(USER, null, OTHER_NOTIFICATION)).isEqualTo(OTHER_NOTIFICATION);
	}

	@Test
	@DisplayName("전체 읽음은 실제 변경된 미읽음 건수를 반환한다")
	void returnsChangedUnreadCount() {
		// given
		when(repository.userExists(USER)).thenReturn(true);
		when(repository.markAllRead(USER)).thenReturn(2L);

		// when / then
		assertThat(service.markAllRead(USER, null)).isEqualTo(2);
		verify(repository).markAllRead(USER);
	}

	@Test
	@DisplayName("선택한 멤버십이 DB 값과 다르면 거절한다")
	void rejectsMembershipThatDiffersFromRegisteredUser() {
		// given
		when(repository.userExists(USER)).thenReturn(true);

		// when / then
		assertThatThrownBy(() -> service.markAllRead(USER, "vip"))
				.isInstanceOf(BusinessException.class)
				.extracting(error -> ((BusinessException) error).getErrorCode())
				.isEqualTo(NotificationErrorCode.USER_MEMBERSHIP_MISMATCH);
		verify(repository).membershipMatches(USER, "vip");
	}
}
