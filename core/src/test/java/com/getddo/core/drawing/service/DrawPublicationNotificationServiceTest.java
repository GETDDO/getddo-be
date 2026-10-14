package com.getddo.core.drawing.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.TransactionDefinition;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.*;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationType;
import com.getddo.core.notification.service.NotificationJobService;
import static com.getddo.core.drawing.exception.DrawPublicationErrorCode.INVALID_PUBLICATION;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 기존 알림 등록 입력과 추첨 공개 트랜잭션 경계를 검증한다. */
class DrawPublicationNotificationServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");
	private final UUID event = UUID.randomUUID(), prize = UUID.randomUUID();
	private final DrawPublicationRepository publications = mock(DrawPublicationRepository.class);
	private final DrawPublicationNotificationRepository recipients = mock(DrawPublicationNotificationRepository.class);
	private final NotificationJobService jobs = mock(NotificationJobService.class);
	private final DrawPublicationNotificationService service = new DrawPublicationNotificationService(publications, recipients, jobs);

	/** 다음 동작을 검증한다: 최초 공개는 접수된 응모자와 공개 ID 발생 키를 기존 알림 작업으로 전달한다. */
	@Test @DisplayName("최초 공개는 접수된 응모자와 공개 ID 발생 키를 기존 알림 작업으로 전달한다")
	void registersInitialRecipientsAndOccurrence() {
		// given
		DrawPublication publication = publication(1);
		List<UUID> users = List.of(UUID.randomUUID(), UUID.randomUUID());
		when(recipients.findParticipants(event)).thenReturn(users);
		// when
		service.register(publication);
		// then
		NotificationJobRequest job = captured();
		assertThat(job.getType()).isEqualTo(NotificationType.RESULT_PUBLISHED);
		assertThat(job.getRecipientIds()).containsExactlyInAnyOrderElementsOf(users);
		assertThat(job.getOccurrenceKey()).isEqualTo("draw-publication:" + publication.getId());
		assertThat(job.getPublicationId()).isEqualTo(publication.getId());
		assertThat(job.getEventId()).isEqualTo(event);
		assertThat(job.getScheduledAt()).isEqualTo(NOW);
		assertThat(job.getLinkUrl()).isEqualTo("/events/" + event + "/results");
		verifyNoInteractions(publications);
	}

	/** 다음 동작을 검증한다: 명단 변경은 유지 당첨자를 제외하고 취소자·새 당첨자를 중복 없이 전달한다. */
	@Test @DisplayName("명단 변경은 유지 당첨자를 제외하고 취소자·새 당첨자를 중복 없이 전달한다")
	void registersOnlyChangedRecipients() {
		// given
		DrawPublication previous = publication(1), current = publication(2);
		UUID canceled = UUID.randomUUID(), winner = UUID.randomUUID(), retained = UUID.randomUUID();
		DrawPublishedSlot keep = new DrawPublishedSlot(prize, 2, UUID.randomUUID(), retained);
		when(publications.findPrevious(event, 2)).thenReturn(Optional.of(previous));
		when(publications.findPublishedSlots(previous.getId())).thenReturn(List.of(
				new DrawPublishedSlot(prize, 1, UUID.randomUUID(), canceled), keep,
				new DrawPublishedSlot(prize, 3, UUID.randomUUID(), canceled)));
		when(publications.findPublishedSlots(current.getId())).thenReturn(List.of(
				new DrawPublishedSlot(prize, 1, UUID.randomUUID(), winner), keep));
		// when
		service.register(current);
		// then
		NotificationJobRequest job = captured();
		assertThat(job.getType()).isEqualTo(NotificationType.RESULT_CHANGED);
		assertThat(job.getRecipientIds()).containsExactlyInAnyOrder(canceled, winner);
		assertThat(job.getOccurrenceKey()).isEqualTo("draw-publication:" + current.getId());
		verifyNoInteractions(recipients);
	}

	/** 다음 동작을 검증한다: 변경 공개의 직전 버전이 없으면 업무 오류로 거절하고 작업을 등록하지 않는다. */
	@Test @DisplayName("변경 공개의 직전 버전이 없으면 업무 오류로 거절하고 작업을 등록하지 않는다")
	void rejectsMissingPreviousPublication() {
		// given
		DrawPublication current = publication(2);
		// when / then
		assertThatThrownBy(() -> service.register(current)).isInstanceOf(BusinessException.class)
				.satisfies(error -> assertThat(((BusinessException) error).getErrorCode()).isEqualTo(INVALID_PUBLICATION));
		verifyNoInteractions(jobs, recipients);
	}

	/** 다음 동작을 검증한다: 트랜잭션 프록시는 공개 트랜잭션 없이 알림 작업 등록을 허용하지 않는다. */
	@Test @DisplayName("트랜잭션 프록시는 공개 트랜잭션 없이 알림 작업 등록을 허용하지 않는다")
	void requiresPublicationTransaction() {
		// given
		AbstractPlatformTransactionManager manager = new AbstractPlatformTransactionManager() {
			/** 활성 트랜잭션이 없는 호출을 시험할 트랜잭션 대역을 반환한다. */
			@Override protected Object doGetTransaction() { return new Object(); }
			/** MANDATORY 등록이 새 트랜잭션을 생성하려 하면 검증을 실패시킨다. */
			@Override protected void doBegin(Object transaction, TransactionDefinition definition) { throw new AssertionError("새 트랜잭션 생성 금지"); }
			/** 신규 트랜잭션을 금지하는 대역의 종료 훅이며 영속 상태를 변경하지 않는다. */
			@Override protected void doCommit(DefaultTransactionStatus status) { }
			/** 트랜잭션 부재 검증에 필요한 대역 종료 훅이며 실제 DB 롤백을 대신하지 않는다. */
			@Override protected void doRollback(DefaultTransactionStatus status) { }
		};
		ProxyFactory factory = new ProxyFactory(service);
		factory.setProxyTargetClass(true);
		factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
		DrawPublicationNotificationService proxy = (DrawPublicationNotificationService) factory.getProxy();
		// when / then
		assertThatThrownBy(() -> proxy.register(publication(1))).isInstanceOf(IllegalTransactionStateException.class);
		verifyNoInteractions(publications, recipients, jobs);
	}

	/** 한 번 등록된 알림 작업 입력을 캡처해 수신 대상과 발생 근거를 검증한다. */
	private NotificationJobRequest captured() {
		ArgumentCaptor<NotificationJobRequest> job = ArgumentCaptor.forClass(NotificationJobRequest.class);
		verify(jobs).register(job.capture());
		return job.getValue();
	}
	/** 차수에 따라 최초·갱신 공개 메타데이터를 구분한 등록 입력을 구성한다. */
	private DrawPublication publication(int revision) {
		return new DrawPublication(UUID.randomUUID(), event, UUID.randomUUID(), revision,
				revision == 1 ? DrawPublicationType.INITIAL : DrawPublicationType.UPDATE,
				revision == 1 ? null : "사유", revision == 1 ? null : UUID.randomUUID(), revision == 1 ? null : NOW, NOW, List.of());
	}
}
