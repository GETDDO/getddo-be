package com.getddo.db.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DB 없이 {@link ConcurrentTasks}의 종료 대기를 검증한다. */
class ConcurrentTasksTest {

	@AfterEach
	void clearInterruptStatus() {
		Thread.interrupted();
	}

	@Test
	@DisplayName("실행 중인 작업이 끝나면 정상적으로 돌아온다")
	void returnsAfterTasksFinish() {
		// given
		ExecutorService executor = Executors.newSingleThreadExecutor();
		AtomicBoolean finished = new AtomicBoolean();
		executor.submit(() -> finished.set(true));
		// when
		ConcurrentTasks.shutdownAndAwait(executor);
		// then
		assertThat(finished).isTrue();
		assertThat(executor.isTerminated()).isTrue();
		assertThat(Thread.currentThread().isInterrupted()).isFalse();
	}

	@Test
	@DisplayName("기다리는 중 interrupt되어도 작업이 끝날 때까지 기다리고 interrupt 상태를 되돌린다")
	void keepsWaitingWhenInterruptedAndRestoresInterruptStatus() throws Exception {
		// given: interrupt를 무시하고 해제될 때까지 도는 작업, 호출 스레드는 이미 interrupt된 상태
		ExecutorService executor = Executors.newSingleThreadExecutor();
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicBoolean finished = new AtomicBoolean();
		executor.submit(() -> {
			started.countDown();
			boolean released = false;
			while (!released) {
				try {
					released = release.await(5, TimeUnit.SECONDS);
				} catch (InterruptedException ignored) {
					// DB 응답을 기다리는 스레드처럼 interrupt로는 멈추지 않는다
				}
			}
			finished.set(true);
		});
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
		Thread releaser = new Thread(() -> {
			try {
				Thread.sleep(300);
			} catch (InterruptedException ignored) {
				// 해제 스레드는 interrupt되지 않는다
			}
			release.countDown();
		});
		releaser.start();
		Thread.currentThread().interrupt();
		// when
		ConcurrentTasks.shutdownAndAwait(executor);
		// then
		assertThat(finished).as("interrupt로 바로 돌아오지 않고 작업 종료를 기다린다").isTrue();
		assertThat(executor.isTerminated()).isTrue();
		assertThat(Thread.interrupted()).as("호출 스레드의 interrupt 상태가 복원된다").isTrue();
		releaser.join();
	}
}
