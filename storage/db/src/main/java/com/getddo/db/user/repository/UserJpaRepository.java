package com.getddo.db.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.user.entity.UserEntity;
import org.springframework.stereotype.Repository;

@Repository
public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {
}
