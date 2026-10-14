package com.getddo.db.drawing.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.getddo.db.drawing.entity.DrawCandidateEntity;

public interface DrawCandidateJpaRepository extends JpaRepository<DrawCandidateEntity, UUID> {
}
