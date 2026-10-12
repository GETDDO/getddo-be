package com.getddo.api.ticket.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.getddo.core.ticket.service.TicketExpiryService;

/**
 * 만료 시각이 지난 응모권을 주기적으로 만료 처리한다.
 *
 * <p>서버 여러 대에서 동시에 돌아도 같은 응모권을 두 번 만료하지 않는다. 서비스가 다른 처리가 잠근 응모권을 건너뛰기
 * 때문이다. 만료 처리가 늦어져도 조회와 차감은 만료 시각을 직접 비교하므로 사용자에게 보이는 보유 수량은 달라지지 않는다.</p>
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
@EnableScheduling
@ConditionalOnProperty(name = "getddo.ticket.expiry.worker.enabled", havingValue = "true", matchIfMissing = true)
public class TicketExpiryWorker {

	/** 밀린 응모권이 많아도 한 번의 폴링이 워커를 오래 붙잡지 않도록 처리할 묶음 수를 제한한다. */
	static final int MAX_BATCHES_PER_POLL = 50;

	private final TicketExpiryService service;
	private final int batchSize;

	/** 만료 서비스와 한 번에 처리할 묶음 크기를 연결한다. */
	public TicketExpiryWorker(TicketExpiryService service,
			@Value("${getddo.ticket.expiry.worker.batch-size:100}") int batchSize) {
		if (batchSize < 1) {
			// 잘못된 값으로 계속 실패하며 만료가 조용히 멈추는 대신 기동 때 바로 드러나게 한다.
			throw new IllegalArgumentException("getddo.ticket.expiry.worker.batch-size는 1 이상이어야 한다: " + batchSize);
		}
		this.service = service;
		this.batchSize = batchSize;
	}

	/** 처리할 응모권이 더 없거나 한 묶음을 채우지 못할 때까지, 최대 {@value #MAX_BATCHES_PER_POLL}묶음을 만료 처리한다. */
	@Scheduled(fixedDelayString = "${getddo.ticket.expiry.worker.poll-delay:60000}")
	public void poll() {
		try {
			for (int batch = 0; batch < MAX_BATCHES_PER_POLL; batch++) {
				if (service.expireDue(batchSize) < batchSize) {
					break;
				}
			}
		} catch (RuntimeException failure) {
			log.warn("Ticket expiry failed: exceptionType={}", failure.getClass().getName());
		}
	}
}
