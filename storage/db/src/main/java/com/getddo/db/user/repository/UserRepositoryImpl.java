package com.getddo.db.user.repository;

import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.user.domain.User;
import com.getddo.core.user.repository.UserRepository;
import com.getddo.db.user.mapper.UserMapper;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

	private final UserJpaRepository userJpaRepository;
	private final UserMapper userMapper;

	@Override
	public Optional<User> findById(UUID userId) {
		return userJpaRepository.findById(userId).map(userMapper::toDomain);
	}
}
