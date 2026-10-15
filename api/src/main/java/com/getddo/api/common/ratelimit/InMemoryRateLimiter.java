package com.getddo.api.common.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.getddo.core.common.time.TimeProvider;

/**
 * 서버 메모리에서 세는 고정 창 방식의 {@link RateLimiter}.
 *
 * <p>키마다 창이 시작된 시각과 횟수를 들고 있다. 창이 끝나면 첫 요청에서 처음부터 다시 센다. 한 서버 안에서만 유효하므로
 * 서버를 여러 대로 늘리면 공유 저장소 구현으로 바꾼다. 서버가 다시 시작하면 횟수도 사라진다. 오래된 키는 일정 호출마다
 * 지워 메모리가 무한히 늘지 않게 한다.</p>
 */
public class InMemoryRateLimiter implements RateLimiter {

	/** 이 횟수만큼 호출될 때마다 끝난 창을 지운다. */
	private static final long CLEANUP_EVERY = 1024;

	private final TimeProvider timeProvider;
	private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
	private final AtomicLong calls = new AtomicLong();

	public InMemoryRateLimiter(TimeProvider timeProvider) {
		this.timeProvider = timeProvider;
	}

	@Override
	public Decision tryAcquire(String key, int limit, Duration window) {
		long now = timeProvider.now().toEpochMilli();
		long windowMillis = window.toMillis();
		// 한도를 넘은 요청은 횟수를 더 올리지 않아 공격 요청이 많아도 값이 계속 커지지 않는다.
		Window current = windows.compute(key, (k, existing) -> {
			if (existing == null || now >= existing.endMillis()) {
				return new Window(now + windowMillis, 1);
			}
			return existing.count() > limit ? existing : new Window(existing.endMillis(), existing.count() + 1);
		});
		if (calls.incrementAndGet() % CLEANUP_EVERY == 0) {
			windows.values().removeIf(stale -> now >= stale.endMillis());
		}
		if (current.count() <= limit) {
			return Decision.allow();
		}
		return Decision.deny((long) Math.ceil((current.endMillis() - now) / 1000.0));
	}

	/** 창이 끝나는 시각과 그 창에서 센 요청 수. */
	private record Window(long endMillis, int count) {
	}
}
