package com.getddo.api.drawing.config;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.getddo.core.drawing.service.DrawExecutionService;
import com.getddo.core.drawing.service.DrawSnapshotService;

/** 마감 후 최초 실행을 등록한다. 등록 커밋 이후의 중단은 기존 복구 워커가 이어 처리한다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "getddo.drawing.initial-worker.enabled", havingValue = "true", matchIfMissing = true)
public class DrawInitialWorker {

	private final DrawSnapshotService snapshots;
	private final DrawExecutionService executions;

	/** 한 이벤트의 실패가 뒤에 있는 마감 이벤트의 등록을 막지 않게 순차 처리한다. */
	@Scheduled(fixedDelayString = "${getddo.drawing.initial-worker.poll-delay:10000}")
	public void poll() {
		try {
			for (UUID eventId : snapshots.findInitialDue()) {
				try {
					UUID runId = snapshots.registerInitial(eventId);
					executions.execute(runId);
				} catch (RuntimeException failure) {
					log.warn("Initial draw deferred: eventId={}, failureType={}", eventId, failure.getClass().getSimpleName());
				}
			}
		} catch (RuntimeException failure) {
			log.warn("Initial draw polling unavailable: failureType={}", failure.getClass().getSimpleName());
		}
	}
}
