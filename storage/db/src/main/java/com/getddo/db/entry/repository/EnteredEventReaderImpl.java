package com.getddo.db.entry.repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.entry.repository.EnteredEventReader;

/**
 * 응모자({@code event_participants})는 접수 완료된 응모가 있을 때만 생기므로, 사용자와 이벤트로 응모자가 있는지만 보면
 * 거절 이력을 제외한 응모 여부가 된다. {@code (event_id, user_id)} 유니크 키로 조회한다.
 */
@Repository
@RequiredArgsConstructor
public class EnteredEventReaderImpl implements EnteredEventReader {

	private final EntityManager entityManager;

	@Override
	public Set<UUID> findEnteredEventIds(UUID userId, Collection<UUID> eventIds) {
		return new HashSet<>(entityManager.createQuery(
				"select p.eventId from EventParticipantEntity p where p.userId = :userId and p.eventId in :eventIds",
				UUID.class)
				.setParameter("userId", userId)
				.setParameter("eventIds", eventIds)
				.getResultList());
	}
}
