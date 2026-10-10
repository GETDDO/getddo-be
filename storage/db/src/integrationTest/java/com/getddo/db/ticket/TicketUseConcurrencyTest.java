package com.getddo.db.ticket;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.ticket.domain.RefundCommand;
import com.getddo.core.ticket.domain.RefundResult;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.domain.UseCommand;
import com.getddo.core.ticket.domain.UseResult;
import com.getddo.core.ticket.domain.UseSelection;
import com.getddo.core.ticket.domain.UsedTicket;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.service.TicketRefundService;
import com.getddo.core.ticket.service.TicketUseService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응모권 차감·반환이 동시에 일어나는 경우를 실제 MySQL 잠금으로 검증한다.
 *
 * <p>앞 트랜잭션이 처리를 마치고 커밋 전에 멈춘 상태에서 뒤 트랜잭션이 실제로 잠금 대기에 들어간 것
 * ({@link LockWaitProbe})을 확인한 뒤 앞 트랜잭션을 풀어 준다. 뒤 트랜잭션은 기본 격리 수준(REPEATABLE READ)에서
 * 앞서 일반 조회를 했더라도, 잠금을 쥔 뒤에는 앞 트랜잭션이 커밋한 최신 값을 봐야 한다.</p>
 */
class TicketUseConcurrencyTest extends TicketIntegrationTestSupport {

	private static final long WAIT_SECONDS = 30;
	private static final String EXPIRES_AT = "2026-09-30T15:00:00Z";
	private static final int REPEATS = 15;

	@Autowired
	private TicketUseService useService;
	@Autowired
	private TicketRefundService refundService;
	@Autowired
	private EntityManager entityManager;

	private UUID ticket() {
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = seeds.missionClaim(userId, parents, 1);
		return insertTicket(userId, claimId, TicketGrade.BRONZE, TicketStatus.AVAILABLE, EXPIRES_AT);
	}

	private UseResult use(UUID entryId, long quantity) {
		return useService.use(new UseCommand(userId, entryId,
				List.of(new UseSelection(TicketGrade.BRONZE, quantity)), "테스트 응모"));
	}

	private static List<UUID> ids(List<UsedTicket> tickets) {
		return tickets.stream().map(UsedTicket::getTicketId).toList();
	}

	@RepeatedTest(REPEATS)
	@DisplayName("보유 3장에 두 응모가 각각 2장을 동시에 요청하면 한쪽만 성공하고 뒤 요청은 TICKET-006이며 2장만 차감된다")
	void competingEntriesCannotOverspend() throws Exception {
		// given
		ticket();
		ticket();
		ticket();
		UUID first = seeds.eventEntry(userId);
		UUID second = seeds.eventEntry(userId);
		// when
		List<Object> outcomes = runWhileFirstHoldsLock(
				() -> use(first, 2),
				() -> use(second, 2));
		// then
		assertThat(outcomes.get(0)).isInstanceOf(UseResult.class);
		assertThat(outcomes.get(1)).isInstanceOfSatisfying(TicketException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INSUFFICIENT));
		assertThat(count("select count(*) from tickets where user_id = ? and status = 'SPENT'", bytes(userId)))
				.isEqualTo(2);
		assertThat(count("select count(*) from ticket_histories where operation_type = 'USE' "
				+ "and event_entry_id in (?, ?)", bytes(first), bytes(second))).isEqualTo(2);
	}

	@RepeatedTest(REPEATS)
	@DisplayName("보유가 충분하면 뒤 요청은 앞 요청이 커밋한 최신 상태를 보고 겹치지 않는 다른 응모권을 차감한다")
	void concurrentEntriesTakeDisjointTickets() throws Exception {
		// given
		for (int i = 0; i < 4; i++) {
			ticket();
		}
		UUID first = seeds.eventEntry(userId);
		UUID second = seeds.eventEntry(userId);
		// when
		List<Object> outcomes = runWhileFirstHoldsLock(
				() -> use(first, 2),
				() -> use(second, 2));
		// then
		assertThat(outcomes.get(0)).isInstanceOf(UseResult.class);
		assertThat(outcomes.get(1)).isInstanceOf(UseResult.class);
		List<UUID> firstIds = ids(((UseResult) outcomes.get(0)).getTickets());
		List<UUID> secondIds = ids(((UseResult) outcomes.get(1)).getTickets());
		assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
		assertThat(count("select count(*) from tickets where user_id = ? and status = 'SPENT' and version = 2",
				bytes(userId))).isEqualTo(4);
	}

	@RepeatedTest(REPEATS)
	@DisplayName("같은 응모의 반환을 동시에 요청하면 뒤 요청은 앞 요청의 결과를 replayed=true로 받고 반환은 한 번이다")
	void sameEntryRefundedOnce() throws Exception {
		// given
		ticket();
		ticket();
		UUID entry = seeds.eventEntry(userId);
		transaction.execute(status -> use(entry, 2));
		// when
		List<Object> outcomes = runWhileFirstHoldsLock(
				() -> refundService.refund(new RefundCommand(entry, "테스트 취소")),
				() -> refundService.refund(new RefundCommand(entry, "테스트 취소")));
		// then
		assertThat(outcomes.get(0)).isInstanceOfSatisfying(RefundResult.class,
				result -> assertThat(result.isReplayed()).isFalse());
		assertThat(outcomes.get(1)).isInstanceOfSatisfying(RefundResult.class,
				result -> assertThat(result.isReplayed()).isTrue());
		assertThat(count("select count(*) from ticket_histories where operation_type = 'REFUND' "
				+ "and ticket_id in (select id from tickets where user_id = ?)", bytes(userId))).isEqualTo(2);
		assertThat(count("select count(*) from tickets where user_id = ? and status = 'RETURNED' and version = 3",
				bytes(userId))).isEqualTo(2);
	}

	/**
	 * 첫 작업이 처리를 마치고 커밋 전에 멈춘 사이 둘째 작업이 시작해 잠금 대기에 들어가게 한 뒤, 첫 작업을 커밋시킨다.
	 * 둘째 작업은 먼저 일반 조회를 하므로 스냅샷이 오래된 상태에서 잠금을 기다리게 된다.
	 *
	 * @return 각 작업의 결과. 성공이면 반환값, 실패면 원인 예외
	 */
	private <T> List<Object> runWhileFirstHoldsLock(Supplier<T> firstWork, Supplier<T> secondWork) throws Exception {
		CountDownLatch firstDone = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<T> first = executor.submit(() -> transaction.execute(status -> {
				T result = firstWork.get();
				// UPDATE·INSERT는 flush 때 나간다. 커밋 전에 내보내 잠금을 쥔 채 멈춘다.
				entityManager.flush();
				firstDone.countDown();
				awaitQuietly(releaseFirst);
				return result;
			}));
			assertThat(firstDone.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			Future<T> second = executor.submit(() -> transaction.execute(status -> secondWork.get()));
			lockWaits.awaitLockWaits(1);
			releaseFirst.countDown();
			return List.of(outcome(first), outcome(second));
		} finally {
			releaseFirst.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	private static <T> Object outcome(Future<T> future) throws Exception {
		try {
			return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			return e.getCause();
		}
	}

	private static void awaitQuietly(CountDownLatch latch) {
		try {
			if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
				throw new IllegalStateException("대기 시간이 지났다.");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}
}
