package com.getddo.api.drawing.config;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import com.getddo.core.drawing.service.DrawExecutionControlService;
import com.getddo.core.drawing.service.DrawExecutionService;

/** 등록 후 시작 누락·영속 예약·만료 시도를 복구한다. 최초 이벤트 자동 등록은 GD-86 책임이다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "getddo.drawing.retry-worker.enabled", havingValue = "true", matchIfMissing = true)
public class DrawRetryWorker {
	private final DrawExecutionControlService control;
	private final DrawExecutionService executions;
	@Scheduled(fixedDelayString = "${getddo.drawing.retry-worker.poll-delay:1000}")
	public void poll() {
		try {
			for (UUID runId : control.findDue()) {
				try { executions.execute(runId); }
				catch (RuntimeException failure) { log.warn("Draw retry deferred: runId={}", runId); }
			}
		} catch (RuntimeException failure) { log.warn("Draw retry polling unavailable"); }
	}
}
