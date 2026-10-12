package com.getddo.api.ticket.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.getddo.core.ticket.service.TicketExpiryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TicketExpiryWorkerTest {

	private static final int BATCH_SIZE = 100;

	@Mock
	private TicketExpiryService service;

	private TicketExpiryWorker worker;

	@BeforeEach
	void setUp() {
		worker = new TicketExpiryWorker(service, BATCH_SIZE);
	}

	@Test
	@DisplayName("한 번에 처리할 묶음 크기가 1 미만이면 워커를 만들 수 없다")
	void rejectsNonPositiveBatchSize() {
		// given
		// when
		// then
		assertThatThrownBy(() -> new TicketExpiryWorker(service, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketExpiryWorker(service, -1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("처리할 응모권이 없으면 한 번만 조회하고 끝낸다")
	void stopsWhenNothingIsDue() {
		// given
		when(service.expireDue(BATCH_SIZE)).thenReturn(0);
		// when
		worker.poll();
		// then
		verify(service, times(1)).expireDue(BATCH_SIZE);
	}

	@Test
	@DisplayName("한 번에 가득 처리됐으면 이어서 계속 처리하고 한 묶음보다 적게 처리되면 끝낸다")
	void keepsGoingWhileBatchesAreFull() {
		// given
		when(service.expireDue(BATCH_SIZE)).thenReturn(BATCH_SIZE, BATCH_SIZE, 30);
		// when
		worker.poll();
		// then
		verify(service, times(3)).expireDue(BATCH_SIZE);
	}

	@Test
	@DisplayName("밀린 응모권이 아주 많아도 한 번의 폴링에서는 최대 묶음 수까지만 처리한다")
	void boundsBatchesPerPoll() {
		// given
		when(service.expireDue(BATCH_SIZE)).thenReturn(BATCH_SIZE);
		// when
		worker.poll();
		// then
		verify(service, times(TicketExpiryWorker.MAX_BATCHES_PER_POLL)).expireDue(BATCH_SIZE);
	}

	@Test
	@DisplayName("처리 중 예외가 나도 폴링 밖으로 던지지 않고 예외 원문은 로그에 남기지 않는다")
	void swallowsFailureWithoutLeakingDetails(CapturedOutput output) {
		// given
		when(service.expireDue(BATCH_SIZE)).thenThrow(new IllegalStateException("private ticket details"));
		// when
		worker.poll();
		// then
		assertThat(output.getOut()).contains(IllegalStateException.class.getName())
				.doesNotContain("private ticket details");
	}
}
