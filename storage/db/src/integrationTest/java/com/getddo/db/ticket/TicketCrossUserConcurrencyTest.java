package com.getddo.db.ticket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.ticket.domain.RefundCommand;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.domain.UseCommand;
import com.getddo.core.ticket.domain.UseSelection;
import com.getddo.core.ticket.service.TicketRefundService;
import com.getddo.core.ticket.service.TicketUseService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 서로 다른 사용자가 서로 다른 응모로 동시에 차감·반환할 때 교착하지 않는지 검증한다.
 *
 * <p>사용자별 응모권 잠금은 서로 막지 않으므로, 이력 인덱스에서 잡는 잠금이 겹치면 교착이 난다. 응모 ID는 운영처럼
 * 시간에 가깝게 정렬되는 UUID v7이다. 모든 트랜잭션이 처리를 마친 뒤 커밋 전에 장벽에서 만나게 해, 이력 INSERT가 같은
 * 시점에 나가도록 순서를 고정한다.</p>
 */
class TicketCrossUserConcurrencyTest extends TicketIntegrationTestSupport {

	private static final long WAIT_SECONDS = 30;
	private static final String EXPIRES_AT = "2026-09-30T15:00:00Z";
	private static final int REPEATS = 15;

	@Autowired
	private TicketUseService useService;
	@Autowired
	private TicketRefundService refundService;

	/** 브론즈 1장을 고른 묶음. */
	private static List<UseSelection> one() {
		return List.of(new UseSelection(TicketGrade.BRONZE, 1));
	}

	private void tickets(UUID user, int count) {
		for (int i = 0; i < count; i++) {
			TicketGrantSeeds.MissionParents parents = seeds.missionParents(user);
			UUID claimId = seeds.missionClaim(user, parents, 1);
			insertTicket(user, claimId, TicketGrade.BRONZE, TicketStatus.AVAILABLE, EXPIRES_AT);
		}
	}

	@RepeatedTest(REPEATS)
	@DisplayName("서로 다른 네 사용자가 서로 다른 응모로 동시에 처음 차감해도 교착 없이 모두 성공한다")
	void differentUsersUseConcurrently() throws Exception {
		// given
		List<UUID> users = new ArrayList<>(List.of(userId));
		for (int i = 0; i < 3; i++) {
			users.add(seeds.user());
		}
		List<UUID> entries = new ArrayList<>();
		List<java.util.function.Supplier<?>> works = new ArrayList<>();
		for (UUID user : users) {
			tickets(user, 2);
			UUID entry = seeds.eventEntry(user);
			entries.add(entry);
			works.add(() -> useService.use(new UseCommand(user, entry, one(), "테스트 응모")));
		}
		// when
		List<Object> outcomes = runTogether(works, true);
		// then
		assertThat(outcomes).noneMatch(Throwable.class::isInstance);
		assertThat(count("select count(*) from ticket_histories where operation_type = 'USE' "
				+ "and event_entry_id in (?, ?, ?, ?)", entries.stream().map(TicketGrantSeeds::bytes).toArray()))
				.isEqualTo(4);
	}

	@RepeatedTest(REPEATS)
	@DisplayName("서로 다른 두 사용자의 서로 다른 응모를 동시에 반환해도 교착 없이 둘 다 성공한다")
	void differentUsersRefundConcurrently() throws Exception {
		// given
		UUID otherUser = seeds.user();
		tickets(userId, 1);
		tickets(otherUser, 1);
		UUID firstEntry = seeds.eventEntry(userId);
		UUID secondEntry = seeds.eventEntry(otherUser);
		transaction.execute(status -> useService.use(new UseCommand(userId, firstEntry, one(), "테스트 응모")));
		transaction.execute(status -> useService.use(new UseCommand(otherUser, secondEntry, one(), "테스트 응모")));
		// when
		List<Object> outcomes = runTogether(List.of(
				() -> refundService.refund(new RefundCommand(firstEntry, "테스트 취소")),
				() -> refundService.refund(new RefundCommand(secondEntry, "테스트 취소"))), true);
		// then
		assertThat(outcomes).noneMatch(Throwable.class::isInstance);
		assertThat(count("select count(*) from ticket_histories where operation_type = 'REFUND' "
				+ "and ticket_id in (select id from tickets where user_id in (?, ?))",
				bytes(userId), bytes(otherUser))).isEqualTo(2);
	}

	/**
	 * 각 작업을 자기 트랜잭션에서 실행한다. {@code alignBeforeCommit}이면 모두 처리를 마친 뒤 커밋 전에 장벽에서 만나게
	 * 한다. 이력 INSERT는 커밋 때 flush되므로 모든 작업이 읽기·잠금을 끝낸 상태에서 동시에 INSERT를 내보내게 된다.
	 *
	 * @return 각 작업의 결과. 성공이면 반환값, 실패면 원인 예외
	 */
	private List<Object> runTogether(List<java.util.function.Supplier<?>> works, boolean alignBeforeCommit)
			throws Exception {
		CyclicBarrier barrier = new CyclicBarrier(works.size());
		ExecutorService executor = Executors.newFixedThreadPool(works.size());
		try {
			List<Future<Object>> futures = new ArrayList<>();
			for (java.util.function.Supplier<?> work : works) {
				futures.add(executor.submit(() -> transaction.execute(status -> {
					Object result = work.get();
					if (alignBeforeCommit) {
						awaitBarrier(barrier);
					}
					return result;
				})));
			}
			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> future : futures) {
				outcomes.add(outcome(future));
			}
			return outcomes;
		} finally {
			barrier.reset();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	private static Object outcome(Future<Object> future) throws Exception {
		try {
			return future.get(WAIT_SECONDS, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			return e.getCause();
		}
	}

	private static void awaitBarrier(CyclicBarrier barrier) {
		try {
			barrier.await(WAIT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (BrokenBarrierException | TimeoutException e) {
			throw new IllegalStateException(e);
		}
	}
}
