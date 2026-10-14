package com.getddo.db.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
		SignalingExecutor executor = new SignalingExecutor();
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
		// 도우미가 interrupt를 받은 뒤 두 번째 awaitTermination에 들어간 것을 확인한 다음에만 작업을 푼다.
		// 신호가 시간 안에 오지 않아도 정리를 위해 작업은 풀고, 신호 여부는 아래에서 검증한다.
		AtomicBoolean signaled = new AtomicBoolean();
		Thread releaser = new Thread(() -> {
			try {
				signaled.set(executor.secondAwaitEntered.await(5, TimeUnit.SECONDS));
			} catch (InterruptedException ignored) {
				// 해제 스레드는 interrupt되지 않는다
			} finally {
				release.countDown();
			}
		});
		releaser.start();
		Thread.currentThread().interrupt();
		// when
		ConcurrentTasks.shutdownAndAwait(executor);
		// 복원된 interrupt 상태가 남아 있으면 아래 join이 InterruptedException을 던질 수 있으므로 먼저 읽고 지운다.
		boolean interruptRestored = Thread.interrupted();
		// then
		releaser.join();
		assertThat(signaled).as("도우미가 interrupt 뒤 두 번째 awaitTermination에 들어갔다").isTrue();
		assertThat(finished).as("interrupt로 바로 돌아오지 않고 작업 종료를 기다린다").isTrue();
		assertThat(executor.isTerminated()).isTrue();
		assertThat(interruptRestored).as("호출 스레드의 interrupt 상태가 복원된다").isTrue();
	}

	/** {@code awaitTermination}의 두 번째 호출 진입을 알리는 테스트용 실행기. */
	private static final class SignalingExecutor extends ThreadPoolExecutor {

		private final CountDownLatch secondAwaitEntered = new CountDownLatch(1);
		private final AtomicInteger awaitCalls = new AtomicInteger();

		SignalingExecutor() {
			super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
			if (awaitCalls.incrementAndGet() == 2) {
				secondAwaitEntered.countDown();
			}
			return super.awaitTermination(timeout, unit);
		}
	}
}
