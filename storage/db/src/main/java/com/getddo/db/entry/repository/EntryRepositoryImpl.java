package com.getddo.db.entry.repository;

import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.entry.domain.Entry;
import com.getddo.core.entry.repository.EntryRepository;
import com.getddo.db.entry.entity.EventEntryEntity;
import com.getddo.db.entry.mapper.EntryMapper;

@Repository
@RequiredArgsConstructor
public class EntryRepositoryImpl implements EntryRepository {

	private final EventEntryJpaRepository jpaRepository;
	private final EventParticipantJpaRepository participantJpaRepository;
	private final EntryMapper mapper;

	@Override
	public Optional<Entry> findById(UUID id) {
		return jpaRepository.findById(id).map(this::toDomain);
	}

	@Override
	public Entry save(Entry entry) {
		jpaRepository.saveAndFlush(mapper.toEntity(entry));
		return entry;
	}

	/** 응모는 이벤트를 응모자를 거쳐서만 안다. 응모자가 없는 응모는 FK가 막으므로 없다고 본다. */
	private Entry toDomain(EventEntryEntity entity) {
		UUID eventId = participantJpaRepository.findById(entity.getParticipantId())
				.orElseThrow(() -> new IllegalStateException("응모의 응모자가 없다."))
				.getEventId();
		return new Entry(entity.getId(), entity.getParticipantId(), eventId, entity.getUserId(),
				entity.getRequestedTicketCount(), entity.getDeductedTicketCount(), entity.getCreatedAt());
	}
}
