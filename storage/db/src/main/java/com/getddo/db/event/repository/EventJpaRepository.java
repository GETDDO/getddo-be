package com.getddo.db.event.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.event.entity.EventEntity;

public interface EventJpaRepository extends JpaRepository<EventEntity, UUID> {
}
