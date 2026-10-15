package com.getddo.core.event.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.service.AuditLogService;
import com.getddo.core.event.domain.EventLifecycleState;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.repository.EventRepository;

/** 한 이벤트의 선점·최신 일정 검증·상태와 감사 저장을 하나의 트랜잭션으로 처리한다. */
@Service
@RequiredArgsConstructor
public class EventLifecycleTransitionService {
	private final EventRepository events;
	private final AuditLogService auditLogs;

	/** 외부 트랜잭션이 없는 모집 상태 순회 서비스에서 호출한다. 잠긴 이벤트는 대기하지 않고 건너뛴다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public boolean advance(UUID eventId, Instant cutoff) {
		EventLifecycleState current = events.findLifecycleByIdForUpdate(eventId).orElse(null);
		if (current == null) { return false; }
		EventStatus next = nextStatus(current, cutoff);
		if (next == current.getStatus()) { return false; }
		events.updateStatus(eventId, next);
		auditLogs.record(new AuditLogCommand(null, "EVENT_STATUS_CHANGE", "EVENT", eventId, null,
				Map.of("status", current.getStatus().name()), Map.of("status", next.name()), null));
		return true;
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
