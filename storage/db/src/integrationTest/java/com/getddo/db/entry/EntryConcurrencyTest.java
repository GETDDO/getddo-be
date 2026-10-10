package com.getddo.db.entry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.entry.domain.EntryReceipt;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.ticket.domain.TicketGrade;

import com.getddo.db.ticket.ConcurrentTasks;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 응모가 동시에 일어나는 경우를 실제 MySQL 잠금으로 검증한다.
 *
 * <p>순서가 중요한 테스트는 앞 트랜잭션이 처리를 마치고 커밋 전에 멈춘 상태에서 뒤 요청이 실제로 잠금 대기에 들어간 것
 * ({@code LockWaitProbe})을 확인한 뒤 앞 트랜잭션을 풀어 준다. 고정 sleep을 쓰지 않는다.</p>
 */
class EntryConcurrencyTest extends EntryIntegrationTestSupport {

	private static final long WAIT_SECONDS = 30;
	private static final int REPEATS = 15;

	/** 응모 처리 트랜잭션처럼 READ COMMITTED로 여는 템플릿. 앞 요청을 커밋 직전에 붙잡아 둘 때 쓴다. */
	private TransactionTemplate readCommitted() {
		TransactionTemplate template = new TransactionTemplate(transaction.getTransactionManager());
		template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
		return template;
	}

	@RepeatedTest(REPEATS)
	@DisplayName("같은 사용자의 첫 응모 두 건이 동시에 와도 누적 상한을 넘지 않고 한 건만 접수된다")
	void sameUserFirstEntriesCannotExceedLimit() throws Exception {
		// given: 각 요청은 3장이라 둘 다 접수되면 상한 5장을 넘는다
		giveTickets(userId, TicketGrade.BRONZE, 6);
		UUID event = weightedEvent(5);
		// when
		List<Object> outcomes = runSimultaneously(List.of(
				() -> entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 3))),
				() -> entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 3)))));
		// then
		assertThat(outcomes.stream().filter(EntryReceipt.class::isInstance)).hasSize(1);
		assertThat(outcomes.stream().filter(Throwable.class::isInstance)).hasSize(1)
				.allSatisfy(failure -> assertEntryError((Throwable) failure, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		assertThat(usedTicketCount(userId, event)).isEqualTo(3);
		assertThat(spentTickets(userId)).isEqualTo(3);
		assertThat(count("select count(*) from event_participants where user_id = ? and event_id = ?",
				bytes(userId), bytes(event))).isEqualTo(1);
	}

	@RepeatedTest(REPEATS)
	@DisplayName("앞 응모가 응모자를 쥔 채 멈춘 사이 같은 사용자의 뒤 응모는 실제로 잠금 대기하고 커밋 뒤 누적 상한을 반영해 거절된다")
	void laterEntryWaitsForParticipantAndSeesCommittedUsage() throws Exception {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 6);
		UUID event = weightedEvent(5);
		CountDownLatch holderDone = new CountDownLatch(1);
		CountDownLatch releaseHolder = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<EntryReceipt> holder = executor.submit(() -> readCommitted().execute(status -> {
				EntryReceipt receipt = entryRecorder.record(
						command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 3)));
				holderDone.countDown();
				await(releaseHolder);
				return receipt;
			}));
			assertThat(holderDone.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			// when: 뒤 요청이 응모자 행(첫 응모면 UNIQUE 키)에서 잠금 대기에 들어간다
			Future<EntryReceipt> waiter = executor.submit(() -> entryService.enter(
					command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 3))));
			lockWaits.awaitLockWaits(1);
			releaseHolder.countDown();
			// then
			assertThat(holder.get(WAIT_SECONDS, TimeUnit.SECONDS).isCreated()).isTrue();
			Throwable failure = failureOf(waiter);
			assertEntryError(failure, EntryErrorCode.TICKET_LIMIT_EXCEEDED);
			assertThat(usedTicketCount(userId, event)).isEqualTo(3);
			assertThat(spentTickets(userId)).isEqualTo(3);
		} finally {
			releaseHolder.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	@RepeatedTest(REPEATS)
	@DisplayName("이미 응모한 사용자의 뒤 응모는 응모자 행 잠금을 기다렸다가 앞 응모가 커밋한 누적 사용량을 반영해 상한을 넘으면 거절된다")
	void returningUserLaterEntryWaitsForParticipantLock() throws Exception {
		// given: 이미 1장을 써서 응모자 행이 있는 사용자. 상한 5장에서 앞 요청 3장이 확정되면 뒤 요청 3장은 넘는다
		giveTickets(userId, TicketGrade.BRONZE, 8);
		UUID event = weightedEvent(5);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 1)));
		CountDownLatch holderDone = new CountDownLatch(1);
		CountDownLatch releaseHolder = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<EntryReceipt> holder = executor.submit(() -> readCommitted().execute(status -> {
				EntryReceipt receipt = entryRecorder.record(
						command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 3)));
				holderDone.countDown();
				await(releaseHolder);
				return receipt;
			}));
			assertThat(holderDone.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			// when
			Future<EntryReceipt> waiter = executor.submit(() -> entryService.enter(
					command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 3))));
			lockWaits.awaitLockWaits(1);
			releaseHolder.countDown();
			// then: 응모자 행을 잠그지 않으면 뒤 요청이 오래된 사용량(1)으로 판정해 합계가 상한 5장을 넘는다
			assertThat(holder.get(WAIT_SECONDS, TimeUnit.SECONDS).isCreated()).isTrue();
			assertEntryError(failureOf(waiter), EntryErrorCode.TICKET_LIMIT_EXCEEDED);
			assertThat(usedTicketCount(userId, event)).isEqualTo(4);
			assertThat(spentTickets(userId)).isEqualTo(4);
		} finally {
			releaseHolder.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	@RepeatedTest(REPEATS)
	@DisplayName("같은 응모 ID가 이미 응모한 사용자에게 동시에 두 번 오면 한 번만 차감하고 나머지는 기존 결과를 돌려준다")
	void sameEntryIdSentTwiceDeductsOnceForReturningUser() throws Exception {
		// given: 이미 응모해 응모자 행이 있는 사용자
		giveTickets(userId, TicketGrade.BRONZE, 6);
		UUID event = weightedEvent(5);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 1)));
		UUID entry = UUID.randomUUID();
		// when
		List<Object> outcomes = runSimultaneously(List.of(
				() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 2))),
				() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 2)))));
		// then
		assertSingleCreatedAndOneReplayed(outcomes);
		assertThat(spentTickets(userId)).isEqualTo(3);
		assertThat(usedTicketCount(userId, event)).isEqualTo(3);
		assertThat(count("select count(*) from event_entries where id = ?", bytes(entry))).isEqualTo(1);
	}

	@RepeatedTest(REPEATS)
	@DisplayName("누적 상한이 빠듯한 사용자에게 같은 응모 ID가 동시에 두 번 와도 뒤 요청은 상한 초과가 아니라 기존 결과를 돌려준다")
	void sameEntryIdSentTwiceReplaysEvenWhenLimitIsTight() throws Exception {
		// given: 상한 5장 중 3장을 이미 썼고, 같은 응모 ID로 2장이 동시에 두 번 온다(앞이 확정되면 사용량이 5로 찬다)
		giveTickets(userId, TicketGrade.BRONZE, 6);
		UUID event = weightedEvent(5);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 3)));
		UUID entry = UUID.randomUUID();
		CountDownLatch holderDone = new CountDownLatch(1);
		CountDownLatch releaseHolder = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<EntryReceipt> holder = executor.submit(() -> readCommitted().execute(status -> {
				EntryReceipt receipt = entryRecorder.record(command(userId, event, entry, tickets(TicketGrade.BRONZE, 2)));
				holderDone.countDown();
				await(releaseHolder);
				return receipt;
			}));
			assertThat(holderDone.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			// when: 같은 응모 ID의 뒤 요청이 응모자 행 잠금을 기다린다
			Future<EntryReceipt> waiter = executor.submit(() -> entryService.enter(
					command(userId, event, entry, tickets(TicketGrade.BRONZE, 2))));
			lockWaits.awaitLockWaits(1);
			releaseHolder.countDown();
			// then: 앞 요청이 접수한 같은 응모이므로 상한 초과가 아니라 기존 결과다
			assertThat(holder.get(WAIT_SECONDS, TimeUnit.SECONDS).isCreated()).isTrue();
			EntryReceipt replayed = waiter.get(WAIT_SECONDS, TimeUnit.SECONDS);
			assertThat(replayed.isCreated()).isFalse();
			assertThat(replayed.getEntryId()).isEqualTo(entry);
			assertThat(spentTickets(userId)).isEqualTo(5);
			assertThat(usedTicketCount(userId, event)).isEqualTo(5);
		} finally {
			releaseHolder.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	@RepeatedTest(REPEATS)
	@DisplayName("같은 응모 ID가 첫 응모로 동시에 두 번 오면 한 번만 차감하고 나머지는 기존 결과를 돌려준다")
	void sameEntryIdSentTwiceDeductsOnceForFirstEntry() throws Exception {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 4);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		// when
		List<Object> outcomes = runSimultaneously(List.of(
				() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 2))),
				() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 2)))));
		// then
		assertSingleCreatedAndOneReplayed(outcomes);
		assertThat(spentTickets(userId)).isEqualTo(2);
		assertThat(usedTicketCount(userId, event)).isEqualTo(2);
		assertThat(count("select count(*) from event_participants where user_id = ? and event_id = ?",
				bytes(userId), bytes(event))).isEqualTo(1);
	}

	@RepeatedTest(REPEATS)
	@DisplayName("서로 다른 사용자 여럿이 같은 이벤트에 서로 다른 응모로 동시에 응모해도 교착 없이 모두 접수된다")
	void differentUsersEnterSameEventWithoutDeadlock() throws Exception {
		// given
		UUID event = weightedEvent(5);
		List<UUID> users = new ArrayList<>(List.of(userId));
		for (int i = 0; i < 3; i++) {
			users.add(seeds.user());
		}
		List<Callable<Object>> works = new ArrayList<>();
		for (UUID user : users) {
			giveTickets(user, TicketGrade.BRONZE, 2);
			works.add(() -> entryService.enter(command(user, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 2))));
		}
		// when
		List<Object> outcomes = runSimultaneously(works);
		// then
		assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).isInstanceOf(EntryReceipt.class));
		assertThat(count("select count(*) from event_participants where event_id = ?", bytes(event))).isEqualTo(4);
		assertThat(count("select count(*) from ticket_histories where operation_type = 'USE'")).isGreaterThanOrEqualTo(8);
	}

	@RepeatedTest(REPEATS)
	@DisplayName("월말 소진용 이벤트(상한 없음)에 같은 사용자의 응모가 여러 건 동시에 와도 보유량을 넘겨 차감하지 않는다")
	void monthEndEventNeverOverspendsHoldings() throws Exception {
		// given: 보유 6장에 2장씩 4건이 동시에 오면 세 건만 접수될 수 있다
		giveTickets(userId, TicketGrade.BRONZE, 6);
		UUID event = weightedEvent(null);
		List<Callable<Object>> works = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			works.add(() -> entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 2))));
		}
		// when
		List<Object> outcomes = runSimultaneously(works);
		// then
		long accepted = outcomes.stream().filter(EntryReceipt.class::isInstance).count();
		assertThat(accepted).isEqualTo(3);
		assertThat(outcomes.stream().filter(Throwable.class::isInstance)).hasSize(1);
		assertThat(spentTickets(userId)).isEqualTo(6);
		assertThat(usedTicketCount(userId, event)).isEqualTo(6);
	}

	@RepeatedTest(REPEATS)
	@DisplayName("추첨이 이벤트 행을 잠그고 있으면 이미 응모한 사용자의 추가 응모도 실제로 잠금 대기했다가 추첨이 끝난 뒤 접수된다")
	void entryWaitsForDrawLockOnEvent() throws Exception {
		// given: 응모자 행이 이미 있어 외래 키 검사가 이벤트 행을 잠그지 않는 사용자라, 이벤트 행 공유 잠금만이 보호한다
		giveTickets(userId, TicketGrade.BRONZE, 2);
		UUID event = weightedEvent(5);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 1)));
		long entriesBefore = count("select count(*) from event_entries");
		CountDownLatch drawLocked = new CountDownLatch(1);
		CountDownLatch releaseDraw = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Long> draw = executor.submit(() -> transaction.execute(status -> {
				jdbc.queryForObject("select count(*) from events where id = ? for update", Long.class, bytes(event));
				drawLocked.countDown();
				await(releaseDraw);
				return count("select count(*) from event_entries");
			}));
			assertThat(drawLocked.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			// when
			Future<EntryReceipt> entry = executor.submit(() -> entryService.enter(
					command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 1))));
			lockWaits.awaitLockWaits(1);
			releaseDraw.countDown();
			// then: 추첨이 잠금을 쥔 동안에는 응모가 들어가지 못했다
			assertThat(draw.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(entriesBefore);
			assertThat(entry.get(WAIT_SECONDS, TimeUnit.SECONDS).isCreated()).isTrue();
			assertThat(usedTicketCount(userId, event)).isEqualTo(2);
		} finally {
			releaseDraw.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	@RepeatedTest(REPEATS)
	@DisplayName("진행 중인 추가 응모가 이벤트 행을 공유 잠금으로 쥐고 있으면 추첨의 배타 잠금은 응모가 커밋될 때까지 기다리고 그 응모를 본다")
	void drawLockWaitsForInFlightEntryAndSeesIt() throws Exception {
		// given: 이미 한 번 응모한 사용자의 추가 응모(외래 키 검사가 이벤트 행을 잠그지 않는다)
		giveTickets(userId, TicketGrade.BRONZE, 2);
		UUID event = weightedEvent(5);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 1)));
		CountDownLatch entryDone = new CountDownLatch(1);
		CountDownLatch releaseEntry = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<EntryReceipt> inFlight = executor.submit(() -> readCommitted().execute(status -> {
				EntryReceipt receipt = entryRecorder.record(
						command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 1)));
				entryDone.countDown();
				await(releaseEntry);
				return receipt;
			}));
			assertThat(entryDone.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
			// when: 추첨 준비가 이벤트 행을 배타 잠금으로 잠그려 한다
			Future<Long> draw = executor.submit(() -> readCommitted().execute(status -> {
				jdbc.queryForObject("select count(*) from events where id = ? for update", Long.class, bytes(event));
				return count("select count(*) from event_entries where user_id = ?", bytes(userId));
			}));
			lockWaits.awaitLockWaits(1);
			releaseEntry.countDown();
			// then
			assertThat(inFlight.get(WAIT_SECONDS, TimeUnit.SECONDS).isCreated()).isTrue();
			assertThat(draw.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(2);
		} finally {
			releaseEntry.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	private static void assertSingleCreatedAndOneReplayed(List<Object> outcomes) {
		assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).isInstanceOf(EntryReceipt.class));
		assertThat(outcomes.stream().map(EntryReceipt.class::cast).filter(EntryReceipt::isCreated)).hasSize(1);
		assertThat(outcomes.stream().map(EntryReceipt.class::cast).filter(receipt -> !receipt.isCreated())).hasSize(1);
	}

	/** 모든 작업을 같은 순간에 시작시키고 각 작업의 결과(반환값 또는 원인 예외)를 모은다. */
	private List<Object> runSimultaneously(List<? extends Callable<?>> works) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(works.size());
		try {
			List<Future<Object>> futures = new ArrayList<>();
			for (Callable<?> work : works) {
				futures.add(executor.submit(() -> {
					start.await();
					return work.call();
				}));
			}
			start.countDown();
			List<Object> outcomes = new ArrayList<>();
			for (Future<Object> future : futures) {
				try {
					outcomes.add(future.get(WAIT_SECONDS, TimeUnit.SECONDS));
				} catch (ExecutionException e) {
					outcomes.add(e.getCause());
				}
			}
			return outcomes;
		} finally {
			start.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	private static Throwable failureOf(Future<?> future) throws Exception {
		try {
			future.get(WAIT_SECONDS, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			return e.getCause();
		}
		throw new AssertionError("실패해야 하는 작업이 성공했다.");
	}

	private static void await(CountDownLatch latch) {
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
