package com.getddo.db.drawing.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.db.drawing.entity.DrawCandidateEntity;
import com.getddo.db.drawing.entity.DrawRunCandidateEntity;
import com.getddo.db.drawing.entity.DrawRunCandidateId;

public interface DrawRunCandidateJpaRepository extends JpaRepository<DrawRunCandidateEntity, DrawRunCandidateId> {
	@Query("""
		select link.candidate from DrawRunCandidateEntity link
		where link.id.drawRunId = :runId order by link.candidate.participantId
		""")
	List<DrawCandidateEntity> findCandidates(@Param("runId") UUID runId);
}
