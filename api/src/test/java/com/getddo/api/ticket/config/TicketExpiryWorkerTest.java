package com.getddo.api.ticket.config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TicketExpiryWorkerTest {

	private static final int BATCH_SIZE = 100;
	private static final long POLL_DELAY_MILLIS = 60_000;

	@Mock
	private TicketExpiryService service;

	private TicketExpiryWorker worker;

	@BeforeEach
	void setUp() {
		worker = new TicketExpiryWorker(service, BATCH_SIZE, POLL_DELAY_MILLIS);
	}

	@Test
	@DisplayName("한 번에 처리할 묶음 크기가 1 미만이면 워커를 만들 수 없다")
	void rejectsNonPositiveBatchSize() {
		// given
		// when
		// then
		assertThatThrownBy(() -> new TicketExpiryWorker(service, 0, POLL_DELAY_MILLIS))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketExpiryWorker(service, -1, POLL_DELAY_MILLIS))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("폴링 간격이 1ms 미만이면 워커를 만들 수 없다")
	void rejectsNonPositivePollDelay() {
		// given
		// when
		// then
		assertThatThrownBy(() -> new TicketExpiryWorker(service, BATCH_SIZE, 0))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TicketExpiryWorker(service, BATCH_SIZE, -1))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("시작하지 않고 종료해도 예외가 나지 않는다")
	void stopWithoutStartIsSafe() {
		// given
		// when
		worker.stop();
		// then
		verify(service, never()).expireDue(BATCH_SIZE);
	}

	@Test
	@DisplayName("시작하면 전용 스레드가 첫 폴링을 바로 실행한다")
	void startRunsFirstPollOnDedicatedThread() throws InterruptedException {
		// given
		CountDownLatch firstPoll = new CountDownLatch(1);
		AtomicReference<String> threadName = new AtomicReference<>();
		when(service.expireDue(BATCH_SIZE)).thenAnswer(invocation -> {
			threadName.set(Thread.currentThread().getName());
			firstPoll.countDown();
			return 0;
		});
		TicketExpiryWorker started = new TicketExpiryWorker(service, BATCH_SIZE, 3_600_000);
		// when
		started.start();
		boolean polled = firstPoll.await(10, TimeUnit.SECONDS);
		started.stop();
		// then
		assertThat(polled).isTrue();
		assertThat(threadName.get()).startsWith(TicketExpiryWorker.THREAD_NAME_PREFIX);
		verify(service, times(1)).expireDue(BATCH_SIZE);
	}

	@Test
	@DisplayName("종료는 진행 중인 폴링이 끝나기 전에는 완료되지 않고 끝난 뒤에 완료된다")
	void stopWaitsForPollInFlight() throws Exception {
		// given
		CountDownLatch inFlight = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(service.expireDue(BATCH_SIZE)).thenAnswer(invocation -> {
			inFlight.countDown();
			release.await(10, TimeUnit.SECONDS);
			return 0;
		});
		TicketExpiryWorker started = new TicketExpiryWorker(service, BATCH_SIZE, 3_600_000);
		ExecutorService stopper = Executors.newSingleThreadExecutor();
		try {
			started.start();
			assertThat(inFlight.await(10, TimeUnit.SECONDS)).isTrue();
			// when
			Future<?> stopping = stopper.submit(started::stop);
			// then
			assertThatThrownBy(() -> stopping.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
			release.countDown();
			stopping.get(10, TimeUnit.SECONDS);
			assertThat(stopping.isDone()).isTrue();
		} finally {
			// 검증이 중간에 실패해도 시작한 전용 스레드가 남지 않게 폴링을 풀어 주고 워커를 종료한다. stop()은 여러 번 불러도 안전하다.
			release.countDown();
			started.stop();
			stopper.shutdownNow();
		}
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
	@DisplayName("처리 중 예외가 나도 폴링 밖으로 던지지 않고 다음 폴링이 이어지며 예외 원문은 로그에 남기지 않는다")
	void swallowsFailureWithoutLeakingDetails(CapturedOutput output) {
		// given
		when(service.expireDue(BATCH_SIZE))
				.thenThrow(new IllegalStateException("private ticket details"))
				.thenReturn(0);
		// when
		worker.poll();
		worker.poll();
		// then
		verify(service, times(2)).expireDue(BATCH_SIZE);
		assertThat(output.getOut()).contains(IllegalStateException.class.getName())
				.doesNotContain("private ticket details");
	}
}
