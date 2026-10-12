package com.getddo.core.event.repository;

import java.util.Optional;
import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public interface EventActorRepository {
	Optional<Actor> findById(UUID userId);

	@Getter
	@RequiredArgsConstructor
	final class Actor {
		private final boolean active;
		private final boolean admin;
	}
}
