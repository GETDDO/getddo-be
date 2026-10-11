package com.getddo.core.notification.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.notification.domain.EventStartNotification;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.repository.EventStartNotificationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventStartNotificationServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-01T03:00:00Z");
	@Mock private EventStartNotificationRepository repository;
	@Mock private NotificationJobService jobs;
	private EventStartNotificationService service;

	@BeforeEach
	void setUp() {
		service = new EventStartNotificationService(repository, jobs,
				new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	}

	@Test
	@DisplayName("시작 알림 대상이 없으면 작업을 등록하지 않는다")
	void doesNothingWithoutDueEvent() {
		when(repository.findNextDueEvent(NOW, NOW.plusSeconds(600))).thenReturn(Optional.empty());
		assertThat(service.registerNextDueEvent()).isFalse();
		verifyNoInteractions(jobs);
	}

	@Test
	@DisplayName("10분 전을 놓친 이벤트는 현재 수신자와 원래 예약 시각으로 작업을 등록한다")
	void registersLateEventWithCurrentRecipients() {
		UUID eventId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		when(repository.findNextDueEvent(NOW, NOW.plusSeconds(600))).thenReturn(Optional.of(
				new EventStartNotification(eventId, "가을 이벤트", NOW.plusSeconds(300), MembershipRule.vip)));
		when(repository.findRecipientIds(MembershipRule.vip)).thenReturn(List.of(userId));

		assertThat(service.registerNextDueEvent()).isTrue();

		ArgumentCaptor<NotificationJobRequest> request = ArgumentCaptor.forClass(NotificationJobRequest.class);
		verify(jobs).register(request.capture());
		assertThat(request.getValue().getOccurrenceKey()).isEqualTo("event-start:" + eventId);
		assertThat(request.getValue().getType()).isEqualTo(NotificationType.EVENT_START);
		assertThat(request.getValue().getEventId()).isEqualTo(eventId);
		assertThat(request.getValue().getScheduledAt()).isEqualTo(NOW.minusSeconds(300));
		assertThat(request.getValue().getRecipientIds()).containsExactly(userId);
		assertThat(request.getValue().getBody()).contains("가을 이벤트");
		assertThat(request.getValue().getLinkUrl()).isEqualTo("/events/" + eventId);
	}

	@Test
	@DisplayName("대상 조회 실패는 성공으로 처리하지 않고 트랜잭션 호출자에게 전달한다")
	void propagatesRecipientLookupFailure() {
		when(repository.findNextDueEvent(NOW, NOW.plusSeconds(600))).thenReturn(Optional.of(
				new EventStartNotification(UUID.randomUUID(), "이벤트", NOW.plusSeconds(600), MembershipRule.vvip)));
		when(repository.findRecipientIds(MembershipRule.vvip)).thenThrow(new IllegalStateException("DB unavailable"));
		assertThatThrownBy(service::registerNextDueEvent).isInstanceOf(IllegalStateException.class);
		verifyNoInteractions(jobs);
	}
}
