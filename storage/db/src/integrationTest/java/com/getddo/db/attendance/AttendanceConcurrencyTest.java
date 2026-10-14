package com.getddo.db.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.mysql.MySQLContainer;

import com.getddo.core.attendance.domain.AttendanceReceipt;
import com.getddo.core.attendance.service.AttendanceRecorder;
import com.getddo.core.attendance.service.AttendanceService;
import com.getddo.db.ticket.ConcurrentTasks;
import com.getddo.db.ticket.LockWaitProbe;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/** 같은 사용자의 같은 날 동시 출석이 한 번만 반영되는지 실제 MySQL 잠금으로 검증한다. */
class AttendanceConcurrencyTest extends AttendanceIntegrationTestSupport {

	private static final long WAIT_SECONDS = 30;

	@Autowired
	private AttendanceService attendanceService;
	@Autowired
	private AttendanceRecorder attendanceRecorder;
	@Autowired
	private MySQLContainer mysql;

	@BeforeEach
	void seedPolicies() {
		policies.dailyPolicy(adminId, 1, Instant.parse("2025-12-31T15:00:00Z"), null);
		policies.streakPolicySet(adminId, LocalDate.parse("2026-01-01"), AttendanceSeeds.DEFAULT_MILESTONES);
	}

	@Test
	@DisplayName("앞 요청이 커밋하기 전에 같은 날 출석이 들어오면 UNIQUE 위반 후 재처리로 앞 요청의 출석을 돌려준다")
	void concurrentRequestRetriesAndReturnsExisting() throws Exception {
		// given: 앞 트랜잭션이 출석·지급을 마치고 커밋 직전에 멈춘다
		CountDownLatch firstRecorded = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<AttendanceReceipt> first = executor.submit(() -> transaction.execute(status -> {
				AttendanceReceipt receipt = attendanceRecorder.record(userId, clock.instant());
				firstRecorded.countDown();
				await(releaseFirst);
				return receipt;
			}));
			assertThat(firstRecorded.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

			// when: 뒤 요청은 출석 저장에서 앞 트랜잭션의 출석 행 잠금을 기다린다
			Future<AttendanceReceipt> second = executor.submit(() -> attendanceService.attend(userId));
			new LockWaitProbe(mysql).awaitLockWaits(1);
			releaseFirst.countDown();
			AttendanceReceipt winner = first.get(WAIT_SECONDS, TimeUnit.SECONDS);
			AttendanceReceipt retried = second.get(WAIT_SECONDS, TimeUnit.SECONDS);

			// then
			assertThat(winner.isCreated()).isTrue();
			assertThat(retried.isCreated()).isFalse();
			assertThat(retried.getAttendanceId()).isEqualTo(winner.getAttendanceId());
			assertThat(retried.getRewards()).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(winner.getRewards());
			assertSingleAttendanceAndGrant();
		} finally {
			releaseFirst.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	@Test
	@DisplayName("호출자가 트랜잭션 안에서 출석을 요청해도 UNIQUE 위반 후 새 트랜잭션에서 재처리되어 기존 출석을 받는다")
	void retriesEvenWhenCalledInsideTransaction() throws Exception {
		// given
		CountDownLatch firstRecorded = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<AttendanceReceipt> first = executor.submit(() -> transaction.execute(status -> {
				AttendanceReceipt receipt = attendanceRecorder.record(userId, clock.instant());
				firstRecorded.countDown();
				await(releaseFirst);
				return receipt;
			}));
			assertThat(firstRecorded.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

			// when: 뒤 요청은 바깥 트랜잭션 안에서 출석을 요청한다
			Future<AttendanceReceipt> second = executor.submit(() ->
					transaction.execute(status -> attendanceService.attend(userId)));
			new LockWaitProbe(mysql).awaitLockWaits(1);
			releaseFirst.countDown();
			AttendanceReceipt winner = first.get(WAIT_SECONDS, TimeUnit.SECONDS);
			AttendanceReceipt retried = second.get(WAIT_SECONDS, TimeUnit.SECONDS);

			// then
			assertThat(retried.isCreated()).isFalse();
			assertThat(retried.getAttendanceId()).isEqualTo(winner.getAttendanceId());
			assertSingleAttendanceAndGrant();
		} finally {
			releaseFirst.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	@Test
	@DisplayName("뒤 요청이 잠금을 기다리는 사이 KST 자정이 지나도 재처리는 첫 요청의 날짜로 앞 요청의 출석을 돌려준다")
	void retryAcrossMidnightKeepsRequestDate() throws Exception {
		// given: 9/15 23:59:59 KST에 앞 트랜잭션이 출석·지급을 마치고 커밋 직전에 멈춘다
		clock.set(Instant.parse("2026-09-15T14:59:59Z"));
		CountDownLatch firstRecorded = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<AttendanceReceipt> first = executor.submit(() -> transaction.execute(status -> {
				AttendanceReceipt receipt = attendanceRecorder.record(userId, clock.instant());
				firstRecorded.countDown();
				await(releaseFirst);
				return receipt;
			}));
			assertThat(firstRecorded.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

			// when: 뒤 요청이 같은 날 잠금을 기다리는 동안 9/16 00:00:01 KST가 된다
			Future<AttendanceReceipt> second = executor.submit(() -> attendanceService.attend(userId));
			new LockWaitProbe(mysql).awaitLockWaits(1);
			clock.set(Instant.parse("2026-09-15T15:00:01Z"));
			releaseFirst.countDown();
			AttendanceReceipt winner = first.get(WAIT_SECONDS, TimeUnit.SECONDS);
			AttendanceReceipt retried = second.get(WAIT_SECONDS, TimeUnit.SECONDS);

			// then
			assertThat(retried.isCreated()).isFalse();
			assertThat(retried.getAttendanceId()).isEqualTo(winner.getAttendanceId());
			assertThat(retried.getAttendanceDate()).isEqualTo(LocalDate.parse("2026-09-15"));
			assertSingleAttendanceAndGrant();
		} finally {
			releaseFirst.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	@Test
	@DisplayName("같은 날 출석 요청 여러 개가 한꺼번에 와도 출석 1건·지급 1회이고 모두 같은 출석을 받는다")
	void manySimultaneousRequests() throws Exception {
		// given
		int requests = 6;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(requests);
		try {
			List<Future<AttendanceReceipt>> futures = new ArrayList<>();
			for (int i = 0; i < requests; i++) {
				futures.add(executor.submit(() -> {
					start.await();
					return attendanceService.attend(userId);
				}));
			}
			// when
			start.countDown();
			List<AttendanceReceipt> receipts = new ArrayList<>();
			for (Future<AttendanceReceipt> future : futures) {
				receipts.add(future.get(WAIT_SECONDS, TimeUnit.SECONDS));
			}
			// then
			assertThat(receipts).extracting(AttendanceReceipt::getAttendanceId)
					.containsOnly(receipts.get(0).getAttendanceId());
			assertThat(receipts).filteredOn(AttendanceReceipt::isCreated).hasSize(1);
			assertSingleAttendanceAndGrant();
		} finally {
			start.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	private void assertSingleAttendanceAndGrant() {
		assertThat(count("select count(*) from attendances where user_id = ?", bytes(userId))).isEqualTo(1);
		assertThat(count("select count(*) from attendance_reward_claims where user_id = ?", bytes(userId)))
				.isEqualTo(1);
		assertThat(count("select count(*) from tickets where user_id = ?", bytes(userId))).isEqualTo(1);
		assertThat(count("""
				select count(*) from ticket_histories h join tickets t on t.id = h.ticket_id
				where t.user_id = ? and h.operation_type = 'GRANT'
				""", bytes(userId))).isEqualTo(1);
		assertThat(count("select consecutive_days from attendance_streaks where user_id = ?", bytes(userId)))
				.isEqualTo(1);
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
