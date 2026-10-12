package com.getddo.api.ticket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.getddo.api.support.ApiIntegrationTest;
import com.getddo.api.ticket.config.TicketExpiryWorker;
import com.getddo.core.ticket.service.TicketExpiryService;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

/**
 * 실제 스케줄러가 설정한 주기와 묶음 크기로 만료 서비스를 호출하는지 확인한다. 만료 처리 자체와 DB 동작은 storage 통합
 * 테스트가 검증하므로 서비스는 목으로 바꾼다.
 */
@ApiIntegrationTest
@TestPropertySource(properties = {
		"getddo.ticket.expiry.worker.enabled=true",
		"getddo.ticket.expiry.worker.poll-delay=20",
		"getddo.ticket.expiry.worker.batch-size=7"
})
class TicketExpiryWorkerIntegrationTest {

	@Autowired
	private TicketExpiryWorker worker;
	@MockitoBean
	private TicketExpiryService service;

	@Test
	@DisplayName("켜 두면 스케줄러가 설정한 묶음 크기로 만료 서비스를 주기적으로 호출한다")
	void schedulerCallsExpiryServiceWithConfiguredBatchSize() {
		// given
		// when
		// then
		assertThat(worker).isNotNull();
		await().atMost(10, SECONDS).untilAsserted(() -> verify(service, atLeast(2)).expireDue(7));
	}
}
