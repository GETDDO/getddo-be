package com.getddo.api.drawing.config;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.getddo.core.drawing.service.DrawExecutionService;
import com.getddo.core.drawing.service.DrawSnapshotService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DrawInitialWorkerTest {

	private final DrawSnapshotService snapshots = mock(DrawSnapshotService.class);
	private final DrawExecutionService executions = mock(DrawExecutionService.class);
	private final DrawInitialWorker worker = new DrawInitialWorker(snapshots, executions);

	@Test
	@DisplayName("한 이벤트의 등록 실패 뒤에도 다음 이벤트를 등록하고 순차 실행한다")
	void isolatesEventFailuresAndExecutesSequentially() {
		// given
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		UUID runId = UUID.randomUUID();
		when(snapshots.findInitialDue()).thenReturn(List.of(first, second));
		when(snapshots.registerInitial(first)).thenThrow(new IllegalStateException());
		when(snapshots.registerInitial(second)).thenReturn(runId);
		// when
		worker.poll();
		// then
		InOrder order = inOrder(snapshots, executions);
		order.verify(snapshots).findInitialDue();
		order.verify(snapshots).registerInitial(first);
		order.verify(snapshots).registerInitial(second);
		order.verify(executions).execute(runId);
		order.verifyNoMoreInteractions();
	}

	@Test
	@DisplayName("대상 조회 장애가 스케줄러 밖으로 전파되지 않는다")
	void isolatesPollingFailure() {
		// given
		when(snapshots.findInitialDue()).thenThrow(new IllegalStateException());
		// when / then
		assertThatCode(worker::poll).doesNotThrowAnyException();
		verifyNoInteractions(executions);
	}

	@Test
	@DisplayName("등록 후 시작 실패는 다음 폴링의 새 등록 없이 복구 서비스에 맡긴다")
	void leavesCommittedRegistrationForRecovery() {
		// given
		UUID eventId = UUID.randomUUID();
		UUID runId = UUID.randomUUID();
		when(snapshots.findInitialDue()).thenReturn(List.of(eventId), List.of());
		when(snapshots.registerInitial(eventId)).thenReturn(runId);
		when(executions.execute(runId)).thenThrow(new IllegalStateException());
		// when
		worker.poll();
		worker.poll();
		// then
		InOrder order = inOrder(snapshots, executions);
		order.verify(snapshots).findInitialDue();
		order.verify(snapshots).registerInitial(eventId);
		order.verify(executions).execute(runId);
		order.verify(snapshots).findInitialDue();
		order.verifyNoMoreInteractions();
	}
}
