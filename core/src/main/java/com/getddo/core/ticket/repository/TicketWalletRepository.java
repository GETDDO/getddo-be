package com.getddo.core.ticket.repository;

import java.time.Instant;
import java.util.UUID;

import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.core.ticket.domain.TicketWalletPeriod;

/** 응모권 지갑 저장소. 모든 메서드는 호출자 트랜잭션 안에서 호출한다. */
public interface TicketWalletRepository {

	/**
	 * 사용자·만료 묶음의 지갑을 잠근 채 반환하고, 없으면 만든다.
	 *
	 * <p>동시에 같은 지갑을 만들려 해도 {@code UNIQUE(user_id, expiry_month)}로 하나만 생긴다.
	 * 충돌한 쪽은 오류 없이 기존 지갑을 잠근다. 잠금은 트랜잭션이 끝날 때까지 유지된다.</p>
	 *
	 * @param userId    소유 사용자 ID
	 * @param period    만료 묶음
	 * @param createdAt 새로 만들 때의 생성 시각. 첫 입금 시각이므로 사용 가능 시작 시각으로도 쓴다
	 * @return 잠긴 지갑
	 */
	TicketWallet getOrCreateForUpdate(UUID userId, TicketWalletPeriod period, Instant createdAt);

	/**
	 * {@link #getOrCreateForUpdate}로 잠근 지갑의 잔액과 version을 반영한다.
	 *
	 * @param wallet 갱신된 지갑
	 */
	void save(TicketWallet wallet);
}
