package com.getddo.api.common.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.common.time.TimeProvider;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryRateLimiterTest {

	private static final Duration MINUTE = Duration.ofMinutes(1);

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-15T03:00:00Z"));
	private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(new TimeProvider(clock));

	@Test
	@DisplayName("한도 안의 요청은 허용하고 한도를 넘으면 거절하며 창이 끝날 때까지 남은 초를 돌려준다")
	void allowsUpToLimitThenDeniesWithRetryAfter() {
		// given
		clock.set(Instant.parse("2026-10-15T03:00:00Z"));
		// when
		// then
		for (int i = 0; i < 3; i++) {
			assertThat(limiter.tryAcquire("k", 3, MINUTE).allowed()).isTrue();
			clock.advance(Duration.ofSeconds(1));
		}
		RateLimiter.Decision denied = limiter.tryAcquire("k", 3, MINUTE);
		assertThat(denied.allowed()).isFalse();
		// 첫 요청 후 60초 창에서 3초가 지났으므로 57초가 남는다.
		assertThat(denied.retryAfterSeconds()).isEqualTo(57);
	}

	@Test
	@DisplayName("창이 끝나면 횟수를 처음부터 다시 센다")
	void resetsAfterWindow() {
		// given
		for (int i = 0; i < 4; i++) {
			limiter.tryAcquire("k", 2, MINUTE);
		}
		assertThat(limiter.tryAcquire("k", 2, MINUTE).allowed()).isFalse();
		// when
		clock.advance(MINUTE);
		// then
		assertThat(limiter.tryAcquire("k", 2, MINUTE).allowed()).isTrue();
		assertThat(limiter.tryAcquire("k", 2, MINUTE).allowed()).isTrue();
		assertThat(limiter.tryAcquire("k", 2, MINUTE).allowed()).isFalse();
	}

	@Test
	@DisplayName("창이 끝나기 직전까지는 같은 창이고 끝나는 순간부터 새 창이다")
	void windowBoundaryIsExclusive() {
		// given
		limiter.tryAcquire("k", 1, MINUTE);
		// when
		clock.advance(MINUTE.minusMillis(1));
		RateLimiter.Decision justBefore = limiter.tryAcquire("k", 1, MINUTE);
		clock.advance(Duration.ofMillis(1));
		RateLimiter.Decision atEnd = limiter.tryAcquire("k", 1, MINUTE);
		// then
		assertThat(justBefore.allowed()).isFalse();
		assertThat(justBefore.retryAfterSeconds()).isEqualTo(1);
		assertThat(atEnd.allowed()).isTrue();
	}

	@Test
	@DisplayName("키마다 따로 센다")
	void keysAreIndependent() {
		// given
		limiter.tryAcquire("a", 1, MINUTE);
		// when
		// then
		assertThat(limiter.tryAcquire("a", 1, MINUTE).allowed()).isFalse();
		assertThat(limiter.tryAcquire("b", 1, MINUTE).allowed()).isTrue();
	}

	@Test
	@DisplayName("거절된 요청이 계속 와도 창이 끝나면 다시 허용한다")
	void deniedRequestsDoNotExtendTheWindow() {
		// given
		for (int i = 0; i < 50; i++) {
			limiter.tryAcquire("k", 1, MINUTE);
			clock.advance(Duration.ofMillis(100));
		}
		// when
		clock.advance(MINUTE);
		// then
		assertThat(limiter.tryAcquire("k", 1, MINUTE).allowed()).isTrue();
	}

	@Test
	@DisplayName("동시에 요청해도 한도 개수만큼만 허용한다")
	void concurrentCallsAreCountedExactly() throws Exception {
		// given
		int callers = 40;
		int limit = 10;
		ExecutorService executor = Executors.newFixedThreadPool(callers);
		CountDownLatch ready = new CountDownLatch(callers);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger allowed = new AtomicInteger();
		try {
			for (int i = 0; i < callers; i++) {
				executor.submit(() -> {
					ready.countDown();
					try {
						go.await(10, TimeUnit.SECONDS);
					} catch (InterruptedException interrupted) {
						Thread.currentThread().interrupt();
						return;
					}
					if (limiter.tryAcquire("shared", limit, MINUTE).allowed()) {
						allowed.incrementAndGet();
					}
				});
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			// when
			go.countDown();
			executor.shutdown();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		} finally {
			go.countDown();
			executor.shutdownNow();
		}
		// then
		assertThat(allowed.get()).isEqualTo(limit);
	}
}
