package com.getddo.core.event.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.RegisteredEvent;

public interface EventRepository {
	RegisteredEvent create(EventRegistration registration, EventStatus initialStatus);

	/** 현재 이벤트와 경품을 잠금 읽기한다. 삭제된 이벤트는 반환하지 않는다. */
	Optional<RegisteredEvent> findByIdForUpdate(UUID eventId);

	/**
	 * 호출자는 같은 트랜잭션에서 동일 eventId의 findByIdForUpdate를 먼저 호출해
	 * 이벤트·경품의 잠금 조회를 완료해야 한다. 확보한 잠금 안에서 설정·경품을 함께 갱신하고 flush한다.
	 * 호출자는 설정의 업무 검증과 기존 경품 ID의 이벤트 소유권 검증을 완료해야 한다.
	 */
	RegisteredEvent update(UUID eventId, EventRegistration settings);

	/** 이벤트 잠금을 확보한 뒤 최초 성공 응모로 생성된 응모자 기록을 최신 읽기로 확인한다. */
	boolean hasParticipationHistory(UUID eventId);

	/** 같은 트랜잭션에서 동일 이벤트의 선행 잠금 조회·삭제 조건 검증 후 논리 삭제하고 flush한다. */
	void softDelete(UUID eventId, Instant deletedAt);
}
