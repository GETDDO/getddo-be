package com.getddo.core.event.repository;

import java.util.Optional;
import java.util.UUID;

public interface EventActorRepository {
	Optional<Actor> findById(UUID userId);

	record Actor(boolean active, boolean admin) {
	}
}
