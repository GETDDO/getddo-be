package com.getddo.core.event.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.repository.EventRepository;

/** 후보를 순환 조회하고 별도 Bean의 이벤트별 트랜잭션을 호출한다. 한 이벤트의 실패는 다음 후보를 막지 않는다. */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventLifecycleService {
	private final EventRepository events;
	private final TimeProvider timeProvider;
	private final EventLifecycleTransitionService transitions;
	private final AtomicReference<UUID> cursor = new AtomicReference<>();

	/**
	 * 외부 트랜잭션 안에서 호출하지 않는다. 후보 조회에는 잠금을 잡지 않고 각 상태 변경만 독립 커밋한다.
	 * 성공·실패와 관계없이 마지막 후보 뒤부터 순환해 반복 실패가 뒷순서 이벤트를 굶기지 않게 한다.
	 */
	@Transactional(propagation = Propagation.NEVER)
	public int advanceDue(int limit) {
		if (limit < 1 || limit > 100) {
			throw new IllegalArgumentException("한 번에 처리할 이벤트 수는 1~100이어야 합니다.");
		}
		Instant cutoff = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
		UUID previous = cursor.get();
		List<UUID> candidates = events.findLifecycleDueIds(cutoff, previous, limit);
		int changed = 0;
		for (UUID eventId : candidates) {
			try {
				if (transitions.advance(eventId, cutoff)) { changed++; }
			} catch (RuntimeException failure) {
				log.warn("Event lifecycle deferred: eventId={}, failureType={}",
						eventId, failure.getClass().getSimpleName());
			}
		}
		// 동시 호출에서 먼저 전진한 커서를 늦게 끝난 이전 호출이 되돌리지 않게 한다.
		cursor.compareAndSet(previous, candidates.isEmpty() ? null : candidates.getLast());
		return changed;
	}
}
