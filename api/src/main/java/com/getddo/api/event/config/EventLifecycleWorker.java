package com.getddo.api.event.config;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.getddo.core.event.service.EventLifecycleService;

/**
 * 전용 스레드 하나에서 최대 20개 이벤트 후보를 순회한다. 느린 알림·추첨과 모집 상태 폴링이 서로 기다리지 않게 한다.
 * 공용 스케줄러 자동 설정에 영향을 주지 않도록 전용 TaskScheduler를 Bean으로 노출하지 않고 직접 관리한다.
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
@ConditionalOnProperty(name = "getddo.event.lifecycle-worker.enabled", havingValue = "true", matchIfMissing = true)
public class EventLifecycleWorker {
	static final String THREAD_NAME_PREFIX = "event-lifecycle-";
	private static final int SHUTDOWN_WAIT_SECONDS = 30;
	private final EventLifecycleService service;
	private final Duration pollDelay;
	private ThreadPoolTaskScheduler scheduler;
	private volatile boolean stopping;

	/** 기존 밀리초 폴링 설정을 사용하고 잘못된 간격은 기동 전에 거절한다. */
	public EventLifecycleWorker(EventLifecycleService service,
			@Value("${getddo.event.lifecycle-worker.poll-delay:1000}") long pollDelayMillis) {
		if (pollDelayMillis < 1) {
			throw new IllegalArgumentException("getddo.event.lifecycle-worker.poll-delay는 1 이상이어야 합니다.");
		}
		this.service = service;
		this.pollDelay = Duration.ofMillis(pollDelayMillis);
	}

	/** 첫 실행은 즉시 시작하고 이전 폴링이 끝난 뒤 설정된 간격만큼 기다린다. 한 워커의 폴링은 겹치지 않는다. */
	@PostConstruct
	void start() {
		ThreadPoolTaskScheduler created = new ThreadPoolTaskScheduler();
		created.setPoolSize(1);
		created.setThreadNamePrefix(THREAD_NAME_PREFIX);
		created.setWaitForTasksToCompleteOnShutdown(true);
		created.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
		created.initialize();
		scheduler = created;
		created.scheduleWithFixedDelay(this::poll, pollDelay);
	}

	/**
	 * 새 폴링을 막고 진행 중인 폴링 종료를 최대 30초 기다린다. 시간을 넘겨도 진행 중인 트랜잭션을 강제 중단하지 않는다.
	 * 이벤트별 상태·감사 원자성과 재기동 후 미처리 이벤트 재조회는 기존 서비스가 보장한다.
	 */
	@PreDestroy
	void stop() {
		stopping = true;
		if (scheduler != null) { scheduler.shutdown(); }
	}

	/** 종료가 시작되기 전에 진입한 폴링은 기존의 bounded 순회를 끝내고, 이후 호출은 새 작업을 시작하지 않는다. */
	public void poll() {
		if (stopping) { return; }
		try {
			service.advanceDue(20);
		} catch (RuntimeException failure) {
			log.warn("Event lifecycle update deferred: failureType={}", failure.getClass().getSimpleName());
		}
	}
}
