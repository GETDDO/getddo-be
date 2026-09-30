package com.getddo.core.user.repository;

import java.util.Optional;
import java.util.UUID;

import com.getddo.core.user.domain.User;

public interface UserRepository {

	Optional<User> findById(UUID userId);
}
