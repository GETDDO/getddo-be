package com.getddo.db.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.getddo.db.notification.entity.NotificationEntity;

/** 알림 목록의 안정적인 정렬과 소유자 범위의 읽음 갱신을 수행한다. */
interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {
	/** 같은 생성 시각의 행도 ID로 순서를 고정해 커서 다음 페이지를 조회한다. */
	@Query("""
		select n from NotificationEntity n
		where n.userId = :userId and (:isRead is null or n.isRead = :isRead)
		and (:cursorAt is null or n.createdAt < :cursorAt
			or (n.createdAt = :cursorAt and n.id < :cursorId))
		order by n.createdAt desc, n.id desc
		""")
	List<NotificationEntity> findMine(@Param("userId") UUID userId,
			@Param("isRead") Boolean isRead, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, Pageable pageable);

	/** 커서 위치와 무관하게 사용자·읽음 필터 전체 건수를 센다. */
	@Query("select count(n) from NotificationEntity n where n.userId = :userId and (:isRead is null or n.isRead = :isRead)")
	long countMine(@Param("userId") UUID userId, @Param("isRead") Boolean isRead);

	/** 이미 읽은 알림의 반복 요청을 성공으로 처리할 때 소유권을 확인한다. */
	boolean existsByIdAndUserId(UUID id, UUID userId);

	/** 본인 미읽음 알림만 갱신해 재요청의 중복 변경을 막는다. */
	@Modifying
	@Query("update NotificationEntity n set n.isRead = true where n.id = :id and n.userId = :userId and n.isRead = false")
	int markRead(@Param("id") UUID id, @Param("userId") UUID userId);

	/** 다른 사용자의 알림과 이미 읽은 알림을 제외하고 일괄 갱신한다. */
	@Modifying
	@Query("update NotificationEntity n set n.isRead = true where n.userId = :userId and n.isRead = false")
	int markAllRead(@Param("userId") UUID userId);
}
