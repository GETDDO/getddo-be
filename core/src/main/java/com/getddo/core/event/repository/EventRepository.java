package com.getddo.core.event.repository;

import java.util.Optional;
import java.util.UUID;

import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.RegisteredEvent;

public interface EventRepository {
	RegisteredEvent create(EventRegistration registration, EventStatus initialStatus);

	/** 현재 이벤트와 경품을 잠금 읽기한다. 삭제된 이벤트는 반환하지 않는다. */
	Optional<RegisteredEvent> findByIdForUpdate(UUID eventId);

	/** findByIdForUpdate로 확보한 잠금 안에서 설정·경품을 함께 갱신하고 flush한다. */
	RegisteredEvent update(UUID eventId, EventRegistration settings);
}
