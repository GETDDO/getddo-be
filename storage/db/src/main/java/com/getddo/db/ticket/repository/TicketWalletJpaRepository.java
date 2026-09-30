package com.getddo.db.ticket.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.getddo.db.ticket.entity.TicketWalletEntity;

public interface TicketWalletJpaRepository extends JpaRepository<TicketWalletEntity, UUID> {

	/** 지갑 행을 {@code SELECT ... FOR UPDATE}로 잠가 조회한다. 잠금 읽기라 다른 트랜잭션이 막 커밋한 행도 보인다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<TicketWalletEntity> findByUserIdAndExpiryMonth(UUID userId, LocalDate expiryMonth);
}
