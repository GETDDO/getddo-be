package com.getddo.core.event.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.exception.EventException;
import com.getddo.core.event.repository.EventRepository;

/** 호출자의 업무 트랜잭션에서 이벤트의 예상 상태를 확인하고 상태만 변경한다. */
@Service
@RequiredArgsConstructor
public class EventStatusChangeService {

	private final EventRepository events;

	/**
	 * 이벤트 행 잠금을 트랜잭션 종료까지 유지하며 삭제·상태 불일치·취소 상태의 복귀를 거절한다.
	 * 예상 상태와 변경할 상태가 현재 상태와 모두 같으면 저장을 생략한다.
	 * 다른 Spring Bean의 기존 트랜잭션 안에서 호출하며, 추첨·알림 등의 연결은 호출자가 처리한다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void changeStatus(UUID eventId, EventStatus expectedStatus, EventStatus newStatus) {
		if (eventId == null) {
			throw new EventException(EventErrorCode.EVENT_NOT_FOUND);
		}
		if (expectedStatus == null || newStatus == null) {
			throw new EventException(EventErrorCode.INVALID_STATUS_CHANGE);
		}
		EventStatus currentStatus = events.findStatusByIdForUpdate(eventId)
				.orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
		if (currentStatus != expectedStatus
				|| (currentStatus == EventStatus.CANCELED && newStatus != currentStatus)) {
			throw new EventException(EventErrorCode.STATUS_CHANGE_CONFLICT);
		}
		if (currentStatus == newStatus) {
			return;
		}
		events.updateStatus(eventId, newStatus);
	}
}
