package com.getddo.db.ticket;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.domain.UseCommand;
import com.getddo.core.ticket.domain.UseSelection;
import com.getddo.core.ticket.service.TicketExpiryService;
import com.getddo.core.ticket.service.TicketUseService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/** 실제 MySQL에서 만료 시각이 지난 응모권의 만료 처리와 그 정합성, 차감과의 경합을 검증한다. */
class TicketExpiryIntegrationTest extends TicketIntegrationTestSupport {

	private static final String DUE = "2026-09-30T15:00:00Z";
	private static final String LATER = "2026-10-31T15:00:00Z";
	private static final Instant DUE_INSTANT = Instant.parse(DUE);
	private static final int REPEATS = 15;
	private static final long WAIT_SECONDS = 30;

	@Autowired
	private TicketExpiryService expiryService;
	@Autowired
	private TicketUseService useService;

	private UUID ticket(TicketGrade grade, TicketStatus status, String expiresAt) {
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = seeds.missionClaim(userId, parents, 1);
		return insertTicket(userId, claimId, grade, status, expiresAt);
	}

	private String statusOf(UUID ticketId) {
		return jdbc.queryForObject("select status from tickets where id = ?", String.class, bytes(ticketId));
	}

	private long versionOf(UUID ticketId) {
		return count("select version from tickets where id = ?", bytes(ticketId));
	}

	private long expireHistories(UUID user) {
		return count("""
				select count(*) from ticket_histories h join tickets t on t.id = h.ticket_id
				where t.user_id = ? and h.operation_type = 'EXPIRE'
				""", bytes(user));
	}

	@Test
	@DisplayName("만료 시각이 지난 사용 가능·반환됨 응모권만 만료됨·버전 +1로 바꾸고 응모권마다 EXPIRE 이력을 남긴다")
	void expiresOnlyDueUsableTickets() {
		// given
		UUID available = ticket(TicketGrade.BRONZE, TicketStatus.AVAILABLE, DUE);
		UUID returned = ticket(TicketGrade.GOLD, TicketStatus.RETURNED, DUE);
		UUID notYet = ticket(TicketGrade.SILVER, TicketStatus.AVAILABLE, LATER);
		UUID spent = ticket(TicketGrade.BRONZE, TicketStatus.SPENT, DUE);
		UUID alreadyExpired = ticket(TicketGrade.BRONZE, TicketStatus.EXPIRED, DUE);
		clock.set(DUE_INSTANT.plusSeconds(60));
		// when
		int expired = expiryService.expireDue(100);
		// then
		assertThat(expired).isEqualTo(2);
		assertThat(statusOf(available)).isEqualTo("EXPIRED");
		assertThat(statusOf(returned)).isEqualTo("EXPIRED");
		assertThat(versionOf(available)).isEqualTo(2);
		assertThat(utc("select updated_at from tickets where id = ?", bytes(available)))
				.isEqualTo(DUE_INSTANT.plusSeconds(60));
		assertThat(utc("select expires_at from tickets where id = ?", bytes(available))).isEqualTo(DUE_INSTANT);
		assertThat(statusOf(notYet)).isEqualTo("AVAILABLE");
		assertThat(statusOf(spent)).isEqualTo("SPENT");
		assertThat(statusOf(alreadyExpired)).isEqualTo("EXPIRED");
		assertThat(versionOf(spent)).isEqualTo(1);
		assertThat(expireHistories(userId)).isEqualTo(2);
		assertThat(count("""
				select count(*) from ticket_histories
				where ticket_id = ? and operation_type = 'EXPIRE' and status = 'EXPIRED' and ticket_version = 2
				  and created_at = ?
				""", bytes(returned), java.time.LocalDateTime.ofInstant(DUE_INSTANT.plusSeconds(60),
				java.time.ZoneOffset.UTC))).isEqualTo(1);
	}

	@Test
	@DisplayName("만료 시각 정각에는 만료하고 그 직전 마이크로초에는 만료하지 않는다")
	void expiryBoundaryIsInclusive() {
		// given
		UUID ticket = ticket(TicketGrade.BRONZE, TicketStatus.AVAILABLE, DUE);
		// when
		// then
		clock.set(DUE_INSTANT.minusNanos(1000));
		assertThat(expiryService.expireDue(10)).isZero();
		assertThat(statusOf(ticket)).isEqualTo("AVAILABLE");
		clock.set(DUE_INSTANT);
		assertThat(expiryService.expireDue(10)).isEqualTo(1);
		assertThat(statusOf(ticket)).isEqualTo("EXPIRED");
	}

	@Test
	@DisplayName("다시 실행해도 이미 만료한 응모권을 또 처리하지 않고, 한 번에 처리할 최대 장수를 지켜 나눠 처리한다")
	void rerunIsIdempotentAndHonorsLimit() {
		// given
		for (int i = 0; i < 5; i++) {
			ticket(TicketGrade.BRONZE, TicketStatus.AVAILABLE, DUE);
		}
		clock.set(DUE_INSTANT.plusSeconds(1));
		// when
		// then
		assertThat(expiryService.expireDue(2)).isEqualTo(2);
		assertThat(expiryService.expireDue(2)).isEqualTo(2);
		assertThat(expiryService.expireDue(2)).isEqualTo(1);
		assertThat(expiryService.expireDue(2)).isZero();
		assertThat(expireHistories(userId)).isEqualTo(5);
		assertThat(count("select count(*) from tickets where user_id = ? and status = 'EXPIRED' and version = 2",
				bytes(userId))).isEqualTo(5);
	}

	@Test
	@DisplayName("만료한 응모권은 차감할 수 없고 만료 처리 전에도 이미 같은 결과였으므로 보유 수량은 달라지지 않는다")
	void expiredTicketsAreNotUsableAndHoldingsDoNotChange() {
		// given
		ticket(TicketGrade.BRONZE, TicketStatus.AVAILABLE, DUE);
		UUID live = ticket(TicketGrade.BRONZE, TicketStatus.AVAILABLE, LATER);
		clock.set(DUE_INSTANT.plusSeconds(1));
		long usableBefore = usableCount();
		// when
		expiryService.expireDue(10);
		// then
		assertThat(usableBefore).isEqualTo(1);
		assertThat(usableCount()).isEqualTo(1);
		assertThat(statusOf(live)).isEqualTo("AVAILABLE");
	}

	private long usableCount() {
		return count("""
				select count(*) from tickets
				where user_id = ? and status in ('AVAILABLE', 'RETURNED') and expires_at > ?
				""", bytes(userId), java.time.LocalDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));
	}

	@RepeatedTest(REPEATS)
	@DisplayName("다른 처리가 잠근 응모권은 기다리지 않고 건너뛰어 그 처리가 끝난 뒤 사용됨으로 남고, 나머지만 만료한다")
	void skipsTicketLockedByInFlightUse() throws Exception {
		// given: 만료 시각 직전에 차감이 응모권을 잠근 채 커밋을 미룬다
		UUID inUse = ticket(TicketGrade.BRONZE, TicketStatus.AVAILABLE, DUE);
		UUID idle = ticket(TicketGrade.GOLD, TicketStatus.AVAILABLE, DUE);
		UUID entry = seeds.eventEntry(userId);
		clock.set(DUE_INSTANT.minusSeconds(1));
		CountDownLatch locked = new CountDownLatch(1);
		CountDownLatch releaseUse = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Object> use = executor.submit(() -> transaction.execute(status -> {
				useService.use(new UseCommand(userId, entry, List.of(new UseSelection(TicketGrade.BRONZE, 1)), "테스트 응모"));
				locked.countDown();
				awaitQuietly(releaseUse);
				return null;
			}));
			assertThat(locked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			clock.set(DUE_INSTANT);
			// when: 만료 처리는 잠금을 기다리지 않고 끝나야 한다(차감은 아직 커밋 전이다)
			Future<Integer> expiry = executor.submit(() -> expiryService.expireDue(10));
			int expired = expiry.get(WAIT_SECONDS, TimeUnit.SECONDS);
			releaseUse.countDown();
			use.get(WAIT_SECONDS, TimeUnit.SECONDS);
			// then
			assertThat(expired).isEqualTo(1);
			assertThat(statusOf(idle)).isEqualTo("EXPIRED");
			assertThat(statusOf(inUse)).isEqualTo("SPENT");
			assertThat(expireHistories(userId)).isEqualTo(1);
		} finally {
			releaseUse.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	@RepeatedTest(REPEATS)
	@DisplayName("두 곳에서 동시에 만료 처리해도 응모권마다 한 번만 만료하고 교착 없이 모두 처리한다")
	void concurrentWorkersExpireEachTicketOnce() throws Exception {
		// given
		int total = 40;
		for (int i = 0; i < total; i++) {
			ticket(TicketGrade.BRONZE, TicketStatus.AVAILABLE, DUE);
		}
		clock.set(DUE_INSTANT.plusSeconds(1));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger sum = new AtomicInteger();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			List<Future<Object>> workers = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				Callable<Object> worker = () -> {
					ready.countDown();
					awaitQuietly(go);
					int expired;
					while ((expired = expiryService.expireDue(7)) > 0) {
						sum.addAndGet(expired);
					}
					return null;
				};
				workers.add(executor.submit(worker));
			}
			assertThat(ready.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			// when
			go.countDown();
			for (Future<Object> worker : workers) {
				worker.get(WAIT_SECONDS, TimeUnit.SECONDS);
			}
			// then
			assertThat(sum.get()).isEqualTo(total);
			assertThat(expireHistories(userId)).isEqualTo(total);
			assertThat(count("select count(*) from tickets where user_id = ? and status = 'EXPIRED' and version = 2",
					bytes(userId))).isEqualTo(total);
		} finally {
			go.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(WAIT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
