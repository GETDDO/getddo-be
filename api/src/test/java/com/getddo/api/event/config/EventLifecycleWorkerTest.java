package com.getddo.api.event.config;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.getddo.core.event.service.EventLifecycleService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventLifecycleWorkerTest {
	@Test
	@DisplayName("한 번 실패한 상태 전환 폴링도 다음 호출에서 다시 처리할 수 있다")
	void continuesAfterFailure() {
		// given
		EventLifecycleService service = mock(EventLifecycleService.class);
		when(service.advanceDue(20)).thenThrow(new IllegalStateException("테스트 실패")).thenReturn(1);
		EventLifecycleWorker worker = new EventLifecycleWorker(service, 1000);
		// when / then
		assertThatCode(worker::poll).doesNotThrowAnyException();
		assertThatCode(worker::poll).doesNotThrowAnyException();
		verify(service, times(2)).advanceDue(20);
	}

	@Test
	@DisplayName("공용 스케줄러가 막혀도 전용 스레드에서 반복 실행하며 공용 Bean은 유지하고 컨텍스트 종료 시 스레드를 정리한다")
	void isolatesPollingWithoutReplacingSharedSchedulerAndClosesItsThread() throws InterruptedException {
		// given
		EventLifecycleService service = mock(EventLifecycleService.class);
		AtomicReference<Thread> eventThread = new AtomicReference<>();
		AtomicReference<CountDownLatch> progress = new AtomicReference<>(new CountDownLatch(0));
		doAnswer(invocation -> {
			eventThread.set(Thread.currentThread());
			progress.get().countDown();
			return 0;
		}).when(service).advanceDue(20);
		// when
		runner(service).run(context -> {
			assertThat(context).hasNotFailed().hasSingleBean(TaskScheduler.class).hasSingleBean(EventLifecycleWorker.class);
			ThreadPoolTaskScheduler shared = context.getBean("taskScheduler", ThreadPoolTaskScheduler.class);
			assertThat(shared.getThreadNamePrefix()).isEqualTo("shared-scheduler-test-");
			CountDownLatch sharedEntered = new CountDownLatch(1);
			CountDownLatch releaseShared = new CountDownLatch(1);
			CountDownLatch sharedFinished = new CountDownLatch(1);
			ScheduledFuture<?> blocking = shared.scheduleWithFixedDelay(() -> {
				sharedEntered.countDown();
				try {
					if (!releaseShared.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("공용 스레드 대기 실패"); }
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(interrupted);
				} finally { sharedFinished.countDown(); }
			}, Duration.ofHours(1));
			try {
				assertThat(sharedEntered.await(10, TimeUnit.SECONDS)).isTrue();
				CountDownLatch twoPolls = new CountDownLatch(2);
				progress.set(twoPolls);
				// then: 공용 스레드가 실제로 멈춘 상태에서 두 번의 이벤트 폴링이 진행된다.
				assertThat(twoPolls.await(10, TimeUnit.SECONDS)).isTrue();
				assertThat(eventThread.get().getName()).startsWith(EventLifecycleWorker.THREAD_NAME_PREFIX);
				assertThat(sharedFinished.getCount()).isEqualTo(1);
			} finally {
				releaseShared.countDown();
				assertThat(sharedFinished.await(10, TimeUnit.SECONDS)).isTrue();
				blocking.cancel(false);
			}
		});
		assertThat(eventThread.get()).isNotNull();
		eventThread.get().join(10_000);
		assertThat(eventThread.get().isAlive()).isFalse();
	}

	@Test
	@DisplayName("종료는 진행 중인 폴링을 기다리고 종료 후에는 새 폴링을 시작하지 않는다")
	void drainsCurrentPollAndRejectsNewPollsAfterShutdown() throws Exception {
		// given
		EventLifecycleService service = mock(EventLifecycleService.class);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(service.advanceDue(20)).thenAnswer(invocation -> {
			entered.countDown();
			if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("이벤트 폴링 대기 실패"); }
			return 0;
		});
		EventLifecycleWorker worker = new EventLifecycleWorker(service, 20);
		try (ExecutorService stopper = Executors.newSingleThreadExecutor()) {
			try {
				worker.start();
				assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
				// when / then
				Future<?> stopping = stopper.submit(worker::stop);
				assertThatThrownBy(() -> stopping.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
				release.countDown();
				stopping.get(10, TimeUnit.SECONDS);
				worker.poll();
				verify(service, times(1)).advanceDue(20);
			} finally {
				release.countDown();
				worker.stop();
			}
		}
	}

	@Test
	@DisplayName("비활성화 설정은 이벤트 워커를 만들지 않고 잘못된 폴링 간격은 기동 전에 거절한다")
	void respectsDisableFlagAndRejectsInvalidDelay() {
		// given / when / then
		EventLifecycleService service = mock(EventLifecycleService.class);
		runner(service).withPropertyValues("getddo.event.lifecycle-worker.enabled=false").run(context ->
				assertThat(context).hasNotFailed().doesNotHaveBean(EventLifecycleWorker.class).hasSingleBean(TaskScheduler.class));
		assertThatThrownBy(() -> new EventLifecycleWorker(service, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new EventLifecycleWorker(service, -1)).isInstanceOf(IllegalArgumentException.class);
	}

	private ApplicationContextRunner runner(EventLifecycleService service) {
		return new ApplicationContextRunner()
				.withConfiguration(AutoConfigurations.of(TaskSchedulingAutoConfiguration.class))
				.withUserConfiguration(SharedScheduling.class, EventLifecycleWorker.class)
				.withBean(EventLifecycleService.class, () -> service)
				.withPropertyValues("getddo.event.lifecycle-worker.poll-delay=20", "spring.task.scheduling.pool.size=1",
						"spring.task.scheduling.thread-name-prefix=shared-scheduler-test-", "spring.threads.virtual.enabled=false");
	}

	@TestConfiguration(proxyBeanMethods = false)
	@EnableScheduling
	static class SharedScheduling { }
}
