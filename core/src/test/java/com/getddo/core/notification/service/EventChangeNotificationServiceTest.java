package com.getddo.core.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.notification.domain.EventNotificationChange;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.exception.NotificationProcessingException;
import com.getddo.core.notification.repository.EventChangeNotificationRepository;
import com.getddo.core.notification.repository.NotificationJobRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventChangeNotificationServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");
	@Mock private EventChangeNotificationRepository repository;
	@Mock private NotificationJobRepository jobRepository;
	@Mock private NotificationJobService jobs;
	@InjectMocks private EventChangeNotificationService service;

	@Test
	@DisplayName("삭제된 이벤트의 시작 작업만 중지하고 별도 안내 작업은 등록하지 않는다")
	void stopsStartGenerationForDeletedEvent() {
		// given
		UUID event = UUID.randomUUID();
		when(repository.isDeletedEvent(event)).thenReturn(true);
		// when
		service.stopForDeletedEvent(event);
		// then
		var order = inOrder(repository);
		order.verify(repository).lockEvent(event);
		order.verify(repository).isDeletedEvent(event);
		order.verify(repository).stopStartJobs(event, "event-deleted:" + event);
		verifyNoInteractions(jobs, jobRepository);
	}

	@Test
	@DisplayName("삭제가 저장되지 않은 이벤트와 null 식별자는 시작 작업을 중지하지 않는다")
	void rejectsUnsavedDeletion() {
		// given
		UUID event = UUID.randomUUID();
		// when / then
		assertThatThrownBy(() -> service.stopForDeletedEvent(null)).isInstanceOf(NotificationProcessingException.class);
		assertThatThrownBy(() -> service.stopForDeletedEvent(event)).isInstanceOf(NotificationProcessingException.class);
		verify(repository, never()).stopStartJobs(any(), any());
		verifyNoInteractions(jobs, jobRepository);
	}

	@Test
	@DisplayName("이전 시작 작업을 멈춘 뒤 실제 수신자에게만 한국 시각으로 변경 안내를 등록한다")
	void registersScheduleChange() {
		// given
		UUID user = UUID.randomUUID();
		UUID job = UUID.randomUUID();
		EventNotificationChange change = EventNotificationChange.scheduleChanged(UUID.randomUUID(),
				UUID.randomUUID(), "가을 이벤트", NOW.plusSeconds(300), NOW.plusSeconds(3600), NOW);
		when(repository.findChangeJobId(change.getOccurrenceKey())).thenReturn(Optional.empty());
		when(repository.matchesSavedChange(change)).thenReturn(true);
		when(repository.findStartRecipientIds(change.getEventId())).thenReturn(List.of(user));
		when(jobs.register(any())).thenReturn(job);
		// when
		assertThat(service.register(change)).isEqualTo(job);
		// then
		ArgumentCaptor<NotificationJobRequest> capture = ArgumentCaptor.forClass(NotificationJobRequest.class);
		var order = inOrder(repository, jobs);
		order.verify(repository).lockEvent(change.getEventId());
		order.verify(repository).findChangeJobId(change.getOccurrenceKey());
		order.verify(repository).matchesSavedChange(change);
		order.verify(repository).stopStartJobs(change.getEventId(), change.getOccurrenceKey());
		order.verify(repository).findStartRecipientIds(change.getEventId());
		order.verify(jobs).register(capture.capture());
		order.verify(repository).linkSourceJob(job, change.getEventId());
		assertThat(capture.getValue().getType()).isEqualTo(NotificationType.EVENT_SCHEDULE_CHANGED);
		assertThat(capture.getValue().getRecipientIds()).containsExactly(user);
		assertThat(capture.getValue().getBody()).contains("12:05:00", "13:00:00", "한국 시간");
		assertThat(capture.getValue().getScheduledAt()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("취소 안내 수신자가 없어도 발생 건을 기록한다")
	void recordsCancellationWithoutRecipients() {
		// given
		EventNotificationChange change = EventNotificationChange.canceled(UUID.randomUUID(),
				UUID.randomUUID(), "이벤트", NOW);
		when(repository.findChangeJobId(change.getOccurrenceKey())).thenReturn(Optional.empty());
		when(repository.matchesSavedChange(change)).thenReturn(true);
		when(repository.findStartRecipientIds(change.getEventId())).thenReturn(List.of());
		// when
		service.register(change);
		// then
		ArgumentCaptor<NotificationJobRequest> capture = ArgumentCaptor.forClass(NotificationJobRequest.class);
		verify(jobs).register(capture.capture());
		assertThat(capture.getValue().getType()).isEqualTo(NotificationType.EVENT_CANCELED);
		assertThat(capture.getValue().getRecipientIds()).isEmpty();
	}

	@Test
	@DisplayName("저장된 이벤트와 다른 변경 입력은 작업을 중지하거나 등록하지 않는다")
	void rejectsUnsavedChange() {
		// given
		EventNotificationChange change = EventNotificationChange.canceled(UUID.randomUUID(),
				UUID.randomUUID(), "이벤트", NOW);
		when(repository.findChangeJobId(change.getOccurrenceKey())).thenReturn(Optional.empty());
		// when / then
		assertThatThrownBy(() -> service.register(change)).isInstanceOf(NotificationProcessingException.class);
		verifyNoInteractions(jobs);
		verify(repository, never()).stopStartJobs(any(), any());
	}

	@Test
	@DisplayName("동일 취소 재요청은 기존 수신자와 작업을 유지한다")
	void returnsExistingJobWithoutRecomputingRecipients() {
		// given
		UUID id = UUID.randomUUID();
		EventNotificationChange change = EventNotificationChange.canceled(UUID.randomUUID(),
				UUID.randomUUID(), "이벤트", NOW);
		when(repository.findChangeJobId(change.getOccurrenceKey())).thenReturn(Optional.of(id));
		when(jobRepository.findRequestForComparison(id)).thenReturn(new NotificationJobRequest(
				change.getOccurrenceKey(), change.getType(), change.getEventId(), null, "이벤트 취소 안내",
				"\"이벤트\" 이벤트가 취소되었습니다.", "/events/" + change.getEventId(), NOW,
				List.of(UUID.randomUUID())));
		// when / then
		assertThat(service.register(change)).isEqualTo(id);
		verifyNoInteractions(jobs);
		verify(repository, never()).findStartRecipientIds(any());
	}
}
