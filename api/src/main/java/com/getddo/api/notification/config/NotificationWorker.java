package com.getddo.api.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.getddo.core.notification.service.NotificationJobService;

/** DB의 미완료 작업을 주기적으로 처리한다. 프로세스 내 작업 큐에만 의존하지 않는다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "getddo.notification.worker.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWorker {
	private final NotificationJobService service;

	/** 정기 폴링에서 호출할 알림 생성·발송 서비스를 연결한다. */
	public NotificationWorker(NotificationJobService service) {
		this.service = service;
	}

	/** 한 번의 조회 주기에서 생성·발송 각각 최대 20건을 처리한다. DB 선점으로 서버 간 중복을 막는다. */
	@Scheduled(fixedDelayString = "${getddo.notification.worker.poll-delay:1000}")
	public void poll() {
		for (int count = 0; count < 20; count++) {
			if (!service.processNextJob()) {
				break;
			}
		}
		for (int count = 0; count < 20; count++) {
			if (!service.processNextDelivery()) {
				break;
			}
		}
	}
}
