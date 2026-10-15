package com.getddo.core.event.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.service.AuditLogService;
import com.getddo.core.event.domain.EventLifecycleState;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.repository.EventRepository;

/** 시작·마감 시각에 따라 모집 상태만 전환하며 추첨·발표·취소는 각 업무 서비스가 처리한다. */
@Service
@RequiredArgsConstructor
public class EventLifecycleService {
	private final EventRepository events;
	private final TimeProvider timeProvider;
	private final AuditLogService auditLogs;

	/**
	 * 한 묶음을 같은 트랜잭션에서 처리한다. 이벤트부터 잠그며 경합 중인 이벤트는 다음 폴링으로 넘긴다.
	 * 서버 중단으로 시작과 마감을 모두 놓친 SCHEDULED는 CLOSED로 바로 전환한다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public int advanceDue(int limit) {
		if (limit < 1 || limit > 100) {
			throw new IllegalArgumentException("한 번에 처리할 이벤트 수는 1~100이어야 합니다.");
		}
		Instant cutoff = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
		int changed = 0;
		for (UUID eventId : events.findLifecycleDueIdsForUpdate(cutoff, limit)) {
			EventLifecycleState current = events.findLifecycleByIdForUpdate(eventId).orElse(null);
			if (current == null) { continue; }
			EventStatus next = nextStatus(current, cutoff);
			if (next != current.getStatus()) {
				events.updateStatus(eventId, next);
				auditLogs.record(new AuditLogCommand(null, "EVENT_STATUS_CHANGE", "EVENT", eventId, null,
						Map.of("status", current.getStatus().name()), Map.of("status", next.name()), null));
				changed++;
			}
		}
		return changed;
	}

	private EventStatus nextStatus(EventLifecycleState current, Instant now) {
		if (current.getStatus() == EventStatus.SCHEDULED && !now.isBefore(current.getStartsAt())) {
			return now.isBefore(current.getEndsAt()) ? EventStatus.OPEN : EventStatus.CLOSED;
		}
		if (current.getStatus() == EventStatus.OPEN && !now.isBefore(current.getEndsAt())) {
			return EventStatus.CLOSED;
		}
		return current.getStatus();
	}
}
