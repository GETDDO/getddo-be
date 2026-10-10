package com.getddo.db.ticket.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.db.ticket.entity.TicketEntity;

public interface TicketJpaRepository extends JpaRepository<TicketEntity, UUID> {

	List<TicketEntity> findByAttendanceRewardClaimId(UUID claimId);

	List<TicketEntity> findByMissionRewardClaimId(UUID claimId);

	List<TicketEntity> findByGameRewardClaimId(UUID claimId);

	/**
	 * 사용자가 지금 쓸 수 있는 응모권 ID를 만료 임박순, 낮은 등급 먼저, ID순으로 잠그지 않고 읽는다.
	 * 등급은 ENUM 정의 순서(BRONZE, SILVER, GOLD)로 정렬된다. 범위 조건에 {@code FOR UPDATE}를 걸면 이웃 사용자의
	 * 행까지 next-key lock이 잡혀 서로 교착하므로, 잠금은 PK로 한 장씩 따로 건다. 건너뛴 행이 있을 수 있어 페이지 단위로
	 * 나누어 읽는다.
	 */
	@Query(value = """
			select id from tickets
			where user_id = :userId and status in ('AVAILABLE', 'RETURNED') and expires_at > :now
			order by expires_at, grade, id
			limit :limit offset :offset
			""", nativeQuery = true)
	List<byte[]> findUsableIds(@Param("userId") byte[] userId, @Param("now") Instant now,
			@Param("limit") int limit, @Param("offset") int offset);
}
