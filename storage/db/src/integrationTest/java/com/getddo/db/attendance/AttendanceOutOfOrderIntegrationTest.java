package com.getddo.db.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/**
 * GD-129: 서로 다른 업무일의 출석 요청이 요청 시각의 반대 순서로 커밋될 때 연속 출석과 단계 보상이 출석 기록과 일치하는지
 * 검증한다.
 *
 * <p>순서는 잠금 대기로 만들지 않고 {@link AttendanceRecorder#record}를 요청 시각을 직접 지정해 차례로 호출해 정한다.
 * 자정 직전 요청이 잠금 대기에서 자정 직후 요청보다 늦게 처리되는 상황을 대기 시간에 의존하지 않고 재현하기 위해서다.
 * 시계는 두 호출 모두 처리 시각(자정 직후)으로 고정한다.</p>
 */
class AttendanceOutOfOrderIntegrationTest extends AttendanceIntegrationTestSupport {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
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

	private static Instant kst(String dateTime) {
		return LocalDateTime.parse(dateTime).atZone(KST).toInstant();
	}

	/** 날짜 범위의 매일 정오(KST)에 순서대로 출석한다. */
	private void attendInOrder(LocalDate from, LocalDate toInclusive) {
		for (LocalDate day = from; !day.isAfter(toInclusive); day = day.plusDays(1)) {
			clock.set(kst(day + "T12:00:00"));
			attendanceService.attend(userId);
		}
	}

	@Test
	@DisplayName("6일 연속 뒤 8일 출석이 7일 출석보다 먼저 커밋돼도 연속 일수는 8이고 7일 단계 보상이 한 번 지급된다")
	void laterDayCommittedBeforeEarlierDayKeepsStreakAndPaysMilestone() {
		// given: 9월 1~6일을 순서대로 출석해 6일 연속이다
		attendInOrder(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-06"));
		// 처리 시각은 8일 자정 직후다. 7일 요청은 자정 직전에 도착해 잠금을 늦게 얻었다고 가정한다.
		clock.set(kst("2026-09-08T00:00:02"));
		Instant requestedOnEighth = kst("2026-09-08T00:00:01");
		Instant requestedOnSeventh = kst("2026-09-07T23:59:59");

		// when: 8일 요청이 먼저 커밋되고 7일 요청이 나중에 커밋된다
		attendanceRecorder.record(userId, requestedOnEighth);
		attendanceRecorder.record(userId, requestedOnSeventh);

		// then: 출석은 1~8일이 모두 있고 순서대로 처리했을 때와 같은 결과여야 한다. 어긋난 곳을 한 번에 보려고 모두 검사한다
		assertSoftly(softly -> {
			softly.assertThat(count("select count(*) from attendances where user_id = ?", bytes(userId)))
					.as("출석 기록 수").isEqualTo(8);
			softly.assertThat(count("select consecutive_days from attendance_streaks where user_id = ?", bytes(userId)))
					.as("순서와 관계없이 1~8일 연속이다").isEqualTo(8);
			softly.assertThat(count("""
					select count(*) from attendance_reward_claims
					where user_id = ? and reward_type = 'STREAK' and source_key = '2026-09:7'
					""", bytes(userId))).as("7일 단계 보상은 7일 출석으로 한 번 청구된다").isEqualTo(1);
			softly.assertThat(count("select count(*) from tickets where user_id = ?", bytes(userId)))
					.as("일일 보상 8장 + 7일 단계 보상 1장").isEqualTo(9);
		});
	}

	@Test
	@DisplayName("대조군: 같은 8일 출석을 7일, 8일 순서로 처리하면 연속 8일이고 7일 단계 보상이 한 번 지급된다")
	void controlProcessedInOrder() {
		// given
		attendInOrder(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-06"));
		clock.set(kst("2026-09-08T00:00:02"));

		// when: 요청 시각 순서대로 커밋한다
		attendanceRecorder.record(userId, kst("2026-09-07T23:59:59"));
		attendanceRecorder.record(userId, kst("2026-09-08T00:00:01"));

		// then
		assertSoftly(softly -> {
			softly.assertThat(count("select consecutive_days from attendance_streaks where user_id = ?", bytes(userId)))
					.as("연속 일수").isEqualTo(8);
			softly.assertThat(count("""
					select count(*) from attendance_reward_claims
					where user_id = ? and reward_type = 'STREAK' and source_key = '2026-09:7'
					""", bytes(userId))).as("7일 단계 보상 청구").isEqualTo(1);
			softly.assertThat(count("select count(*) from tickets where user_id = ?", bytes(userId)))
					.as("응모권").isEqualTo(9);
		});
	}

	@Test
	@DisplayName("1~5일과 7일이 먼저 저장된 뒤 6일이 늦게 처리되면 연속 7일이 되고 7일 단계 보상은 이미 저장된 7일 출석으로 청구된다")
	void lateMiddleDayClaimsMilestoneOnStoredDay() {
		// given
		attendInOrder(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-05"));
		clock.set(kst("2026-09-08T00:00:02"));
		attendanceRecorder.record(userId, kst("2026-09-07T12:00:00"));

		// when: 6일 요청이 가장 늦게 커밋된다
		attendanceRecorder.record(userId, kst("2026-09-06T23:59:59"));

		// then
		assertSoftly(softly -> {
			softly.assertThat(count("select consecutive_days from attendance_streaks where user_id = ?", bytes(userId)))
					.as("연속 일수").isEqualTo(7);
			softly.assertThat(count("""
					select count(*) from attendance_reward_claims c join attendances a on a.id = c.attendance_id
					where c.user_id = ? and c.reward_type = 'STREAK' and c.source_key = '2026-09:7'
					  and a.attendance_date = '2026-09-07' and c.reward_date = '2026-09-07'
					""", bytes(userId))).as("7일 단계 청구는 7일 출석에 연결된다").isEqualTo(1);
			softly.assertThat(count("select count(*) from tickets where user_id = ?", bytes(userId)))
					.as("일일 보상 7장 + 7일 단계 보상 1장").isEqualTo(8);
		});
	}

	@Test
	@DisplayName("7일 요청이 8일 요청의 커밋을 잠금으로 기다렸다가 처리돼도 연속 8일이고 7일 단계 보상은 한 번 지급된다")
	void earlierDayWaitsForLaterDayCommit() throws Exception {
		// given: 1~6일 출석. 8일 요청이 연속 현황을 잠근 채 커밋 직전에 멈춘다
		attendInOrder(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-06"));
		clock.set(kst("2026-09-07T23:59:59"));
		assertRecordedAfterHolderCommits(kst("2026-09-08T00:00:01"), kst("2026-09-07T23:59:59"));
	}

	@Test
	@DisplayName("8일 요청이 7일 요청의 커밋을 잠금으로 기다렸다가 처리돼도 연속 8일이고 7일 단계 보상은 한 번 지급된다")
	void laterDayWaitsForEarlierDayCommit() throws Exception {
		// given: 1~6일 출석. 7일 요청이 연속 현황을 잠근 채 커밋 직전에 멈춘다
		attendInOrder(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-06"));
		clock.set(kst("2026-09-08T00:00:01"));
		assertRecordedAfterHolderCommits(kst("2026-09-07T23:59:59"), kst("2026-09-08T00:00:01"));
	}

	/**
	 * 먼저 {@code holderRequestedAt} 요청이 연속 현황 잠금을 쥔 채 멈추고, {@code waiterRequestedAt}에 해당하는 시계로
	 * 들어온 요청이 그 잠금을 기다리게 한 뒤 앞 요청을 커밋시킨다. 기다리던 요청은 앞 요청이 커밋한 출석을 보고 다시
	 * 계산해야 한다.
	 */
	private void assertRecordedAfterHolderCommits(Instant holderRequestedAt, Instant waiterClock) throws Exception {
		CountDownLatch holderRecorded = new CountDownLatch(1);
		CountDownLatch releaseHolder = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<AttendanceReceipt> holder = executor.submit(() -> transaction.execute(status -> {
				AttendanceReceipt receipt = attendanceRecorder.record(userId, holderRequestedAt);
				holderRecorded.countDown();
				await(releaseHolder);
				return receipt;
			}));
			assertThat(holderRecorded.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();

			// when: 기다리는 요청은 운영 경로(AttendanceService)로 들어와 연속 현황 잠금을 기다린다
			clock.set(waiterClock);
			Future<AttendanceReceipt> waiter = executor.submit(() -> attendanceService.attend(userId));
			new LockWaitProbe(mysql).awaitLockWaits(1);
			releaseHolder.countDown();
			holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
			waiter.get(WAIT_SECONDS, TimeUnit.SECONDS);

			// then: 두 요청이 어떤 순서로 커밋되어도 순서대로 처리했을 때와 같다
			assertSoftly(softly -> {
				softly.assertThat(count("select count(*) from attendances where user_id = ?", bytes(userId)))
						.as("출석 기록 수").isEqualTo(8);
				softly.assertThat(count("select consecutive_days from attendance_streaks where user_id = ?", bytes(userId)))
						.as("연속 일수").isEqualTo(8);
				softly.assertThat(count("""
						select count(*) from attendance_reward_claims
						where user_id = ? and reward_type = 'STREAK' and source_key = '2026-09:7'
						""", bytes(userId))).as("7일 단계 보상 청구는 한 번").isEqualTo(1);
				softly.assertThat(count("select count(*) from tickets where user_id = ?", bytes(userId)))
						.as("일일 보상 8장 + 7일 단계 보상 1장").isEqualTo(9);
			});
		} finally {
			releaseHolder.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
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
