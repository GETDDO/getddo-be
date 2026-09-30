package com.getddo.db.ticket.repository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 현재 트랜잭션이 {@code SELECT ... FOR UPDATE}로 잠근 지갑 ID 목록.
 *
 * <p>지갑 갱신이 잠금 뒤에만 일어나는지 확인하는 데 쓴다. Hibernate의 Entity 잠금 모드는 UPDATE를 flush한 뒤
 * {@code WRITE}로 바뀌어 DB 잠금이 유지되는지 알려 주지 못하므로, 잠근 사실을 트랜잭션 리소스에 직접 기록한다.</p>
 *
 * <p>목록은 트랜잭션이 끝나면 지운다. {@code REQUIRES_NEW} 등으로 트랜잭션이 잠시 멈추면 목록도 떼어 두었다가
 * 다시 붙여, 안쪽 트랜잭션이 바깥 트랜잭션의 잠금을 자기 것으로 보지 않게 한다.</p>
 */
final class LockedWallets implements TransactionSynchronization {

	private static final Object RESOURCE_KEY = LockedWallets.class;

	private final Set<UUID> walletIds = new HashSet<>();

	private LockedWallets() {
	}

	/** 현재 트랜잭션이 지갑을 잠갔다고 기록한다. */
	static void register(UUID walletId) {
		current().walletIds.add(walletId);
	}

	/** 현재 트랜잭션이 이 지갑을 잠갔는지 확인한다. */
	static boolean isLocked(UUID walletId) {
		Object bound = TransactionSynchronizationManager.getResource(RESOURCE_KEY);
		return bound instanceof LockedWallets locked && locked.walletIds.contains(walletId);
	}

	private static LockedWallets current() {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			throw new IllegalStateException("응모권 지갑은 트랜잭션 안에서만 잠글 수 있다.");
		}
		LockedWallets bound = (LockedWallets) TransactionSynchronizationManager.getResource(RESOURCE_KEY);
		if (bound != null) {
			return bound;
		}
		LockedWallets created = new LockedWallets();
		TransactionSynchronizationManager.bindResource(RESOURCE_KEY, created);
		TransactionSynchronizationManager.registerSynchronization(created);
		return created;
	}

	@Override
	public void suspend() {
		TransactionSynchronizationManager.unbindResource(RESOURCE_KEY);
	}

	@Override
	public void resume() {
		TransactionSynchronizationManager.bindResource(RESOURCE_KEY, this);
	}

	@Override
	public void afterCompletion(int status) {
		TransactionSynchronizationManager.unbindResourceIfPossible(RESOURCE_KEY);
	}
}
