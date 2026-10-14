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
	 * 미삭제 이벤트 행을 잠그고 최신 상태를 읽는다. 호출자 트랜잭션에서 사용한다.
	 * 호출자의 미반영 변경을 flush한 뒤 해당 이벤트의 기존 Entity 참조를 분리하고 다시 적재한다.
	 * 영속성 구현의 호출자는 상태 변경 후 이전 Entity 참조로 수정하지 않고 저장소에서 재조회한다.
	 */
	Optional<EventStatus> findStatusByIdForUpdate(UUID eventId);

	/**
	 * 같은 트랜잭션에서 findStatusByIdForUpdate로 동일 이벤트를 잠그고 예상 상태·취소 상태 검증을
	 * 완료한 뒤 호출한다. 설정·경품은 변경하지 않고 상태를 반영해 flush한다.
	 */
	void updateStatus(UUID eventId, EventStatus newStatus);

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
