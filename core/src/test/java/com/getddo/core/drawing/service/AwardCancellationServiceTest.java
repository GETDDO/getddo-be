package com.getddo.core.drawing.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import com.getddo.core.drawing.domain.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AwardCancellationServiceTest {
	private final UUID actor = UUID.randomUUID(), win = UUID.randomUUID(), key = UUID.randomUUID(), run = UUID.randomUUID();
	private final AwardCancellationRegistrationService registration = mock(AwardCancellationRegistrationService.class);
	private final AwardCancellationQueryService query = mock(AwardCancellationQueryService.class);
	private final DrawExecutionService execution = mock(DrawExecutionService.class);
	private final AwardCancellationService service = new AwardCancellationService(registration, query, execution);

	@Test @DisplayName("등록 반환 뒤 같은 실행 ID·실제 관리자로 공통 실행을 호출한다")
	void executesRegisteredRunAfterRegistrationReturns() {
		// given
		AwardCancellation cancellation = cancellation();
		when(registration.register(actor, win, key, "사유")).thenReturn(cancellation);
		DrawExecutionResult confirmed = new DrawExecutionResult(run, DrawRunStatus.CONFIRMED,
				new DrawConfirmation(run, UUID.randomUUID(), Instant.EPOCH, List.of()));
		when(execution.execute(run, actor)).thenReturn(confirmed);
		// when
		AwardCancellationReceipt receipt = service.cancel(actor, win, key, "사유");
		// then
		assertThat(receipt.execution()).isSameAs(confirmed);
		InOrder order = inOrder(registration, execution);
		order.verify(registration).register(actor, win, key, "사유");
		order.verify(execution).execute(run, actor);
		verify(execution, never()).resume(any(), any());
	}

	@Test @DisplayName("키 UNIQUE 경합은 롤백된 등록 대신 기존 취소와 실행을 사용한다")
	void recoversCommittedKeyCollision() {
		// given
		when(registration.register(actor, win, key, "사유")).thenThrow(new DataIntegrityViolationException("collision"));
		when(registration.replay(actor, win, key, "사유")).thenReturn(Optional.of(cancellation()));
		// when
		service.cancel(actor, win, key, "사유");
		// then
		verify(execution).execute(run, actor);
	}

	@Test @DisplayName("멱등 키 충돌이 아닌 저장 오류는 실행하지 않고 전파한다")
	void doesNotHideUnrelatedStorageFailure() {
		// given
		when(registration.register(actor, win, key, "사유")).thenThrow(new DataIntegrityViolationException("failure"));
		when(registration.replay(actor, win, key, "사유")).thenReturn(Optional.empty());
		// when / then
		assertThatThrownBy(() -> service.cancel(actor, win, key, "사유")).isInstanceOf(DataIntegrityViolationException.class);
		verifyNoInteractions(execution);
	}

	@Test @DisplayName("AD09 실패 재개는 기존 실행으로 위임하고 새 취소·등록은 만들지 않는다")
	void resumesExistingFailedRun() {
		// given
		UUID cancellationId = cancellation().id();
		when(query.findConnected(actor, cancellationId)).thenReturn(snapshot(DrawRunStatus.FAILED));
		// when
		service.startOrResume(actor, cancellationId);
		// then
		verify(execution).resume(run, actor);
		verifyNoInteractions(registration);
	}

	@Test @DisplayName("AD09 확정 재요청은 공통 기능의 저장 결과를 그대로 반환한다")
	void replaysConfirmedRun() {
		// given
		UUID cancellationId = cancellation().id();
		when(query.findConnected(actor, cancellationId)).thenReturn(snapshot(DrawRunStatus.CONFIRMED));
		DrawExecutionResult confirmed = new DrawExecutionResult(run, DrawRunStatus.CONFIRMED,
				new DrawConfirmation(run, UUID.randomUUID(), Instant.EPOCH, List.of()));
		when(execution.execute(run, actor)).thenReturn(confirmed);
		// when / then
		assertThat(service.startOrResume(actor, cancellationId)).isSameAs(confirmed);
		verify(execution, never()).resume(any(), any());
		verifyNoInteractions(registration);
	}

	private AwardCancellation cancellation() {
		return new AwardCancellation(UUID.randomUUID(), win, run, actor, "사유", Instant.EPOCH, key);
	}
	private DrawSnapshot snapshot(DrawRunStatus status) {
		return new DrawSnapshot(run, UUID.randomUUID(), status, null, null, null, List.of(), DrawType.REPLACEMENT);
	}
}
