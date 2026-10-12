package com.getddo.db.entry.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.entry.entity.EventEntryEntity;

public interface EventEntryJpaRepository extends JpaRepository<EventEntryEntity, UUID> {
}
