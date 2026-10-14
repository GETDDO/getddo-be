package com.getddo.db.ticket;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 테스트가 현재 시각을 정하는 시계.
 *
 * <p>{@link #tickEveryRead}를 켜면 읽을 때마다 1ms씩 진행한다. 한 번의 지급이 시계를 한 번만 읽는지,
 * 즉 여러 행의 생성 시각이 같은 값인지 확인할 때 쓴다.</p>
 */
public class MutableClock extends Clock {

	private static final Duration TICK = Duration.ofMillis(1);

	private final AtomicReference<Instant> current;
	private volatile boolean tickEveryRead;

	public MutableClock(Instant initial) {
		this.current = new AtomicReference<>(initial);
	}

	public void set(Instant instant) {
		current.set(instant);
		tickEveryRead = false;
	}

	public void tickEveryRead(boolean enabled) {
		tickEveryRead = enabled;
	}

	@Override
	public Instant instant() {
		if (tickEveryRead) {
			return current.getAndUpdate(value -> value.plus(TICK));
		}
		return current.get();
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException("테스트 시계는 UTC만 지원한다.");
	}
}
