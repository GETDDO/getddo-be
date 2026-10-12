package com.getddo.api.ticket.config;

import java.time.Duration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.getddo.core.ticket.service.TicketExpiryService;

/**
 * 만료 시각이 지난 응모권을 주기적으로 만료 처리한다.
 *
 * <p>서버 여러 대에서 동시에 돌아도 같은 응모권을 두 번 만료하지 않는다. 서비스가 다른 처리가 잠근 응모권을 건너뛰기
 * 때문이다. 만료 처리가 늦어져도 조회와 차감은 만료 시각을 직접 비교하므로 사용자에게 보이는 보유 수량은 달라지지 않는다.</p>
 *
 * <p>{@code @Scheduled}는 스레드 하나짜리 공용 스케줄러를 알림·추첨 워커와 나눠 쓴다. 한 번에 많은 응모권을 처리하는
 * 동안 그 워커들이 밀리지 않도록 이 워커는 자기 전용 스레드에서 돈다. 공용 스케줄러를 대체하는 {@code TaskScheduler} 빈을
 * 등록하면 다른 워커의 스케줄러까지 바뀌므로, 빈으로 노출하지 않고 이 클래스가 직접 생성하고 종료한다.</p>
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
@ConditionalOnProperty(name = "getddo.ticket.expiry.worker.enabled", havingValue = "true", matchIfMissing = true)
public class TicketExpiryWorker {

	/** 밀린 응모권이 많아도 한 번의 폴링이 워커를 오래 붙잡지 않도록 처리할 묶음 수를 제한한다. */
	static final int MAX_BATCHES_PER_POLL = 50;
	static final String THREAD_NAME_PREFIX = "ticket-expiry-";
	/** 종료할 때 진행 중인 묶음이 끝나기를 기다리는 최대 시간. 묶음은 하나의 트랜잭션이라 중간에 끊지 않는다. */
	private static final int SHUTDOWN_WAIT_SECONDS = 30;

	private final TicketExpiryService service;
	private final int batchSize;
	private final Duration pollDelay;
	private ThreadPoolTaskScheduler scheduler;

	/** 만료 서비스, 한 번에 처리할 묶음 크기, 폴링 간격(밀리초)을 연결한다. */
	public TicketExpiryWorker(TicketExpiryService service,
			@Value("${getddo.ticket.expiry.worker.batch-size:100}") int batchSize,
			@Value("${getddo.ticket.expiry.worker.poll-delay:60000}") long pollDelayMillis) {
		if (batchSize < 1) {
			// 잘못된 값으로 계속 실패하며 만료가 조용히 멈추는 대신 기동 때 바로 드러나게 한다.
			throw new IllegalArgumentException("getddo.ticket.expiry.worker.batch-size는 1 이상이어야 한다: " + batchSize);
		}
		if (pollDelayMillis < 1) {
			throw new IllegalArgumentException(
					"getddo.ticket.expiry.worker.poll-delay는 1 이상이어야 한다: " + pollDelayMillis);
		}
		this.service = service;
		this.batchSize = batchSize;
		this.pollDelay = Duration.ofMillis(pollDelayMillis);
	}

	/** 전용 스레드 하나로 이전 실행이 끝난 뒤 폴링 간격만큼 쉬고 다시 실행하도록 등록한다. */
	@PostConstruct
	void start() {
		ThreadPoolTaskScheduler created = new ThreadPoolTaskScheduler();
		created.setPoolSize(1);
		created.setThreadNamePrefix(THREAD_NAME_PREFIX);
		created.setWaitForTasksToCompleteOnShutdown(true);
		created.setAwaitTerminationSeconds(SHUTDOWN_WAIT_SECONDS);
		created.initialize();
		created.scheduleWithFixedDelay(this::poll, pollDelay);
		scheduler = created;
	}

	/** 새 실행을 멈추고 진행 중인 묶음이 끝나기를 기다린다. */
	@PreDestroy
	void stop() {
		if (scheduler != null) {
			scheduler.shutdown();
		}
	}

	/** 처리할 응모권이 더 없거나 한 묶음을 채우지 못할 때까지, 최대 {@value #MAX_BATCHES_PER_POLL}묶음을 만료 처리한다. */
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
