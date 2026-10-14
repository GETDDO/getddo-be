package com.getddo.db.event.repository;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.LockMode;
import org.hibernate.Locking;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.RegisteredEvent;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.exception.EventException;
import com.getddo.core.event.repository.EventRepository;
import com.getddo.db.event.entity.EventEntity;
import com.getddo.db.event.entity.EventPrizeEntity;
import com.getddo.db.event.mapper.EventEntityMapper;

@Repository
public class EventRepositoryImpl implements EventRepository {
	private final EventJpaRepository jpaRepository;
	private final EventEntityMapper mapper;
	private final EntityManager entityManager;

	public EventRepositoryImpl(EventJpaRepository jpaRepository, EventEntityMapper mapper, EntityManager entityManager) {
		this.jpaRepository = jpaRepository;
		this.mapper = mapper;
		this.entityManager = entityManager;
	}

	@Override
	public RegisteredEvent create(EventRegistration registration, EventStatus initialStatus) {
		EventEntity saved = jpaRepository.saveAndFlush(new EventEntity(registration, initialStatus));
		return mapper.toRegisteredEvent(saved, saved.getPrizes().stream()
				.sorted(Comparator.comparingInt(EventPrizeEntity::getRank)).toList());
	}

	@Override
	// Hibernate 7.4에서는 fetch 대상 잠금을 지정하는 Scope API가 필요하다. JPA 4 전환 시 대체한다.
	@SuppressWarnings("removal")
	public Optional<RegisteredEvent> findByIdForUpdate(UUID eventId) {
		// 이벤트 행을 먼저 잠가 같은 이벤트의 수정 요청을 경품 조회 전에 직렬화한다.
		// 아래 fetch 조회에서도 이벤트를 잠그지만, 이벤트 → 경품의 잠금 순서를 명시적으로 고정한다.
		if (jpaRepository.findByIdForUpdate(eventId).isEmpty()) {
			return Optional.empty();
		}
		// Hibernate 7의 기본 잠금 범위는 루트뿐이다. 경품도 현재 값을 읽도록 fetch 대상까지 잠근다.
		return entityManager.unwrap(Session.class).createSelectionQuery(
				"select e from EventEntity e left join fetch e.prizes where e.id = :id and e.deletedAt is null",
				EventEntity.class)
				.setParameter("id", eventId)
				.setHibernateLockMode(LockMode.PESSIMISTIC_WRITE)
				.setLockScope(Locking.Scope.INCLUDE_FETCHES)
				.uniqueResultOptional().map(this::toDomain);
	}

	@Override
	public Optional<EventStatus> findStatusByIdForUpdate(UUID eventId) {
		// 호출자의 미반영 변경을 보존한 뒤 refresh해 1차 캐시에 남은 이전 상태로 판정하지 않는다.
		entityManager.flush();
		return jpaRepository.findByIdForUpdate(eventId).map(event -> {
			entityManager.refresh(event, LockModeType.PESSIMISTIC_WRITE);
			return event.getStatus();
		});
	}

	/** 선행 상태 잠금 조회가 관리 중인 Entity를 재사용하며 상태와 공통 수정 시각만 저장한다. */
	@Override
	public void updateStatus(UUID eventId, EventStatus newStatus) {
		EventEntity event = jpaRepository.findById(eventId)
				.filter(saved -> saved.getDeletedAt() == null)
				.orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
		event.changeStatus(newStatus);
		jpaRepository.flush();
	}

	/**
	 * 같은 트랜잭션·영속성 컨텍스트에서 동일 eventId의 findByIdForUpdate를 먼저 완료해야 한다.
	 * findById는 선행 조회로 관리 중인 Entity를 재사용하며, 획득한 잠금은 트랜잭션 종료까지 유지된다.
	 * JPA의 중간·최종 flush로 등수 UNIQUE와 GD-70 JDBC 조회의 가시성을 함께 보장한다.
	 */
	@Override
	public RegisteredEvent update(UUID eventId, EventRegistration settings) {
		EventEntity event = jpaRepository.findById(eventId)
				.filter(saved -> saved.getDeletedAt() == null)
				.orElseThrow(() -> new EventException(EventErrorCode.EVENT_NOT_FOUND));
		event.preparePrizeReplacement(settings.getPrizes());
		jpaRepository.flush();
		event.modify(settings);
		jpaRepository.flush();
		return toDomain(event);
	}

	private RegisteredEvent toDomain(EventEntity event) {
		return mapper.toRegisteredEvent(event, event.getPrizes().stream()
				.sorted(Comparator.comparingInt(EventPrizeEntity::getRank)).toList());
	}
}
