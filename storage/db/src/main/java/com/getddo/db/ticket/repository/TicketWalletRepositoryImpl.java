package com.getddo.db.ticket.repository;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.uuid.UuidVersion7Strategy;
import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.core.ticket.domain.TicketWalletPeriod;
import com.getddo.core.ticket.repository.TicketWalletRepository;
import com.getddo.db.common.util.UuidBinary;
import com.getddo.db.ticket.entity.TicketWalletEntity;
import com.getddo.db.ticket.mapper.TicketWalletMapper;

/**
 * 응모권 지갑 저장소 구현.
 *
 * <p>지갑 확보는 두 단계다. 먼저 네이티브 {@code INSERT ... ON DUPLICATE KEY UPDATE}로 지갑이 있도록 만든다.
 * 이미 있으면 아무 값도 바꾸지 않는다. 그다음 {@code SELECT ... FOR UPDATE}로 잠가 조회한다.
 * JPA {@code persist}로 만들면 동시 생성의 UNIQUE 위반이 세션과 트랜잭션을 못 쓰게 만들어 같은 트랜잭션에서
 * 다시 조회할 수 없기 때문이다.</p>
 */
@Repository
@RequiredArgsConstructor
public class TicketWalletRepositoryImpl implements TicketWalletRepository {

	private static final String INSERT_IF_ABSENT = """
			insert into ticket_wallets
			  (id, user_id, expiry_month, valid_from, expires_at, balance, status, version, created_at, updated_at)
			values
			  (:id, :userId, :expiryMonth, :createdAt, :expiresAt, 0, 'ACTIVE', 0, :createdAt, :createdAt)
			on duplicate key update id = id
			""";

	private final EntityManager entityManager;
	private final TicketWalletJpaRepository walletJpaRepository;
	private final TicketWalletMapper walletMapper;

	@Override
	public TicketWallet getOrCreateForUpdate(UUID userId, TicketWalletPeriod period, Instant createdAt) {
		entityManager.createNativeQuery(INSERT_IF_ABSENT)
				.setParameter("id", UuidBinary.toBytes(newWalletId()))
				.setParameter("userId", UuidBinary.toBytes(userId))
				.setParameter("expiryMonth", period.getExpiryMonth())
				.setParameter("expiresAt", period.getExpiresAt())
				.setParameter("createdAt", createdAt)
				.executeUpdate();
		TicketWalletEntity wallet = walletJpaRepository
				.findByUserIdAndExpiryMonth(userId, period.getExpiryMonth())
				.orElseThrow(() -> new IllegalStateException("확보한 응모권 지갑을 조회하지 못했다."));
		LockedWallets.register(wallet.getId());
		return walletMapper.toDomain(wallet);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>잠그지 않고 읽은 잔액으로 갱신하면 동시 갱신에서 잔액과 version이 어긋나므로, 현재 트랜잭션에서
	 * {@link #getOrCreateForUpdate}로 잠근 지갑이 아니면 거절한다.</p>
	 *
	 * @throws IllegalStateException 현재 트랜잭션에서 잠그지 않은 지갑인 경우
	 */
	@Override
	public void save(TicketWallet wallet) {
		if (!LockedWallets.isLocked(wallet.getId())) {
			throw new IllegalStateException("잠그지 않은 응모권 지갑은 갱신할 수 없다.");
		}
		// 잠근 Entity가 영속성 컨텍스트에 있으므로 추가 조회 없이 반환된다.
		TicketWalletEntity entity = entityManager.find(TicketWalletEntity.class, wallet.getId());
		entity.applyDeposit(wallet.getBalance(), wallet.getVersion());
	}

	/** 공통 Entity와 같은 Hibernate UUID v7 생성기로 새 지갑 ID를 만든다. */
	private UUID newWalletId() {
		return UuidVersion7Strategy.INSTANCE.generateUuid(
				entityManager.unwrap(SharedSessionContractImplementor.class));
	}
}
