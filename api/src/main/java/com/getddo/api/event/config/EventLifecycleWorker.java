package com.getddo.api.event.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.getddo.core.event.service.EventLifecycleService;

/** 매 폴링에서 최대 20개 이벤트의 모집 상태를 반영한다. 다음 실행이 미처리 이벤트를 이어 처리한다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "getddo.event.lifecycle-worker.enabled", havingValue = "true", matchIfMissing = true)
public class EventLifecycleWorker {
	private final EventLifecycleService service;

	@Scheduled(fixedDelayString = "${getddo.event.lifecycle-worker.poll-delay:1000}")
	public void poll() {
		try {
			service.advanceDue(20);
		} catch (RuntimeException failure) {
			log.warn("Event lifecycle update deferred: failureType={}", failure.getClass().getSimpleName());
		}
	}
}
