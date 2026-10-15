package com.getddo.api.event.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.event.service.EventLifecycleService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventLifecycleWorkerTest {
	@Test
	@DisplayName("한 번 실패한 상태 전환 폴링도 다음 호출에서 다시 처리할 수 있다")
	void continuesAfterFailure() {
		// given
		EventLifecycleService service = mock(EventLifecycleService.class);
		when(service.advanceDue(20)).thenThrow(new IllegalStateException("테스트 실패")).thenReturn(1);
		EventLifecycleWorker worker = new EventLifecycleWorker(service);
		// when / then
		assertThatCode(worker::poll).doesNotThrowAnyException();
		assertThatCode(worker::poll).doesNotThrowAnyException();
		verify(service, org.mockito.Mockito.times(2)).advanceDue(20);
	}
}
