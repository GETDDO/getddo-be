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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.TransientDataAccessResourceException;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.notification.domain.MockDeliveryStatus;
import com.getddo.core.notification.domain.NotificationDelivery;
import com.getddo.core.notification.domain.NotificationJob;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.exception.NotificationProcessingErrorCode;
import com.getddo.core.notification.exception.NotificationProcessingException;
import com.getddo.core.notification.repository.NotificationJobRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationJobServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
	private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final NotificationJob JOB = new NotificationJob(UUID.randomUUID(), 1);
	private static final NotificationDelivery DELIVERY = new NotificationDelivery(UUID.randomUUID(), FIRST,
			"제목", "내용", null, 1);

	@Mock private NotificationJobRepository repository;
	@Mock private MockNotificationSender sender;
	private NotificationJobService service;

	/** 서비스가 사용할 고정 업무 시각을 준비한다. */
	@BeforeEach
	void setUp() {
		service = new NotificationJobService(repository, new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)), sender);
	}

	/** 검증 시나리오: 일부 사용자 생성 후 일시적 실패는 별도 실패 기록과 재시도로 넘긴다. */
	@Test
	@DisplayName("일부 사용자 생성 후 일시적 실패는 별도 실패 기록과 재시도로 넘긴다")
	void recordsGenerationFailureWithoutSending() {
		// given
		NotificationJobRequest request = request(List.of(FIRST, SECOND));
		when(repository.claimNextJob(NOW)).thenReturn(Optional.of(JOB));
		when(repository.findRequest(JOB.getId())).thenReturn(request);
		when(repository.createNotification(JOB, request, FIRST, NOW)).thenReturn(true);
		when(repository.createNotification(JOB, request, SECOND, NOW))
				.thenThrow(new TransientDataAccessResourceException("private details"));

		// when / then
		assertThat(service.processNextJob()).isTrue();
		verify(repository).failJob(JOB, NOW.plusSeconds(10), "NOTIFICATION-005");
		verify(repository, never()).completeJob(JOB, NOW);
		verifyNoInteractions(sender);
	}

	/** 검증 시나리오: 선점이 교체되면 이전 처리자는 생성 완료나 실패를 덮어쓰지 않는다. */
	@Test
	@DisplayName("선점이 교체되면 이전 처리자는 생성 완료나 실패를 덮어쓰지 않는다")
	void stopsWhenClaimIsLost() {
		// given
		NotificationJobRequest request = request(List.of(FIRST));
		when(repository.claimNextJob(NOW)).thenReturn(Optional.of(JOB));
		when(repository.findRequest(JOB.getId())).thenReturn(request);
		when(repository.createNotification(JOB, request, FIRST, NOW)).thenReturn(false);

		// when / then
		assertThat(service.processNextJob()).isTrue();
		verify(repository, never()).completeJob(any(), any());
		verify(repository, never()).failJob(any(), any(), any());
	}

	/** 검증 시나리오: 대상이 없는 작업도 완료하며 발송을 수행하지 않는다. */
	@Test
	@DisplayName("대상이 없는 작업도 완료하며 발송을 수행하지 않는다")
	void completesEmptyRecipientSet() {
		// given
		when(repository.claimNextJob(NOW)).thenReturn(Optional.of(JOB));
		when(repository.findRequest(JOB.getId())).thenReturn(request(List.of()));

		// when / then
		assertThat(service.processNextJob()).isTrue();
		verify(repository).completeJob(JOB, NOW);
		verifyNoInteractions(sender);
	}

	/** 검증 시나리오: 모의 발송 성공은 기존 알림의 발송 결과만 저장한다. */
	@Test
	@DisplayName("모의 발송 성공은 기존 알림의 발송 결과만 저장한다")
	void completesDeliverySeparately() {
		// given
		when(repository.claimNextDelivery(NOW)).thenReturn(Optional.of(DELIVERY));
		when(sender.send(DELIVERY)).thenReturn(MockDeliveryStatus.SENT);

		// when / then
		assertThat(service.processNextDelivery()).isTrue();
		verify(repository).completeDelivery(DELIVERY, NOW);
		verify(repository, never()).claimNextJob(NOW);
	}

	/** 검증 시나리오: 모의 발송 실패 결과는 생성 재시도와 구분해서 10초 뒤 재시도한다. */
	@Test
	@DisplayName("모의 발송 실패 결과는 생성 재시도와 구분해서 10초 뒤 재시도한다")
	void retriesFailedMockDelivery() {
		// given
		when(repository.claimNextDelivery(NOW)).thenReturn(Optional.of(DELIVERY));
		when(sender.send(DELIVERY)).thenReturn(MockDeliveryStatus.FAILED);

		// when / then
		assertThat(service.processNextDelivery()).isTrue();
		verify(repository).failDelivery(DELIVERY, NOW.plusSeconds(10), "NOTIFICATION-005");
		verify(repository, never()).completeDelivery(DELIVERY, NOW);
	}

	/** 검증 시나리오: 모의 발송 입력 오류는 즉시 최종 실패 처리한다. */
	@Test
	@DisplayName("모의 발송 입력 오류는 즉시 최종 실패 처리한다")
	void doesNotRetryInvalidDelivery() {
		// given
		when(repository.claimNextDelivery(NOW)).thenReturn(Optional.of(DELIVERY));
		when(sender.send(DELIVERY)).thenThrow(new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB));

		// when / then
		assertThat(service.processNextDelivery()).isTrue();
		verify(repository).failDelivery(DELIVERY, null, "NOTIFICATION-003");
	}

	/** 각 시나리오에서 발생 키와 수신 대상을 지정할 유효한 알림 작업 입력을 만든다. */
	private static NotificationJobRequest request(List<UUID> users) {
		return new NotificationJobRequest("발생-1", NotificationType.RESULT_CHANGED,
				null, null, "제목", "내용", null, NOW, users);
	}
}
