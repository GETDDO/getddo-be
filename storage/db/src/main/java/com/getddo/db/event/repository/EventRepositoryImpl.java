package com.getddo.db.event.repository;

import java.util.Comparator;

import org.springframework.stereotype.Repository;

import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.RegisteredEvent;
import com.getddo.core.event.repository.EventRepository;
import com.getddo.db.event.entity.EventEntity;
import com.getddo.db.event.entity.EventPrizeEntity;
import com.getddo.db.event.mapper.EventEntityMapper;

@Repository
public class EventRepositoryImpl implements EventRepository {
	private final EventJpaRepository jpaRepository;
	private final EventEntityMapper mapper;

	public EventRepositoryImpl(EventJpaRepository jpaRepository, EventEntityMapper mapper) {
		this.jpaRepository = jpaRepository;
		this.mapper = mapper;
	}

	@Override
	public RegisteredEvent create(EventRegistration registration, EventStatus initialStatus) {
		EventEntity saved = jpaRepository.saveAndFlush(new EventEntity(registration, initialStatus));
		return mapper.toRegisteredEvent(saved, saved.getPrizes().stream()
				.sorted(Comparator.comparingInt(EventPrizeEntity::getRank)).toList());
	}
}
