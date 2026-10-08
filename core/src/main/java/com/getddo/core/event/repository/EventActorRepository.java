package com.getddo.core.event.repository;

import java.util.Optional;
import java.util.UUID;

import com.getddo.core.event.domain.MembershipRule;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

public interface EventActorRepository {
	Optional<Actor> findById(UUID userId);

	@Getter
	@RequiredArgsConstructor
	final class Actor {
		private final boolean active;
		private final boolean admin;
		private final MembershipRule membership;

		public Actor(boolean active, boolean admin) {
			this(active, admin, null);
		}
	}
}
