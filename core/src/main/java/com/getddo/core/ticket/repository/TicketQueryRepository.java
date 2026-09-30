package com.getddo.core.ticket.repository;

import java.util.List;
import java.util.UUID;

import com.getddo.core.ticket.domain.TicketLedgerCursor;
import com.getddo.core.ticket.domain.TicketLedgerFilter;
import com.getddo.core.ticket.domain.TicketTransactionView;
import com.getddo.core.ticket.domain.TicketWallet;

/** 사용자 화면용 응모권 조회. 지갑과 원장을 읽기만 한다. */
public interface TicketQueryRepository {

	/**
	 * 사용자의 모든 지갑을 만료월 최신순으로 조회한다. 만료된 지갑도 포함한다.
	 *
	 * @param userId 사용자 ID
	 * @return 저장된 상태 그대로의 지갑 목록. 없으면 빈 목록
	 */
	List<TicketWallet> findWallets(UUID userId);

	/**
	 * 사용자의 이력을 {@code createdAt DESC, id DESC} 순서로 조회한다.
	 *
	 * @param userId 사용자 ID
	 * @param filter 조회 조건
	 * @param after  이 커서보다 뒤의 행부터 조회한다. 첫 조회면 null
	 * @param limit  최대 행 수
	 * @return 파생 값을 채운 이력 목록
	 */
	List<TicketTransactionView> findLedger(UUID userId, TicketLedgerFilter filter, TicketLedgerCursor after,
			int limit);

	/**
	 * 커서와 관계없이 조회 조건에 맞는 전체 이력 수를 센다.
	 *
	 * @param userId 사용자 ID
	 * @param filter 조회 조건
	 * @return 전체 이력 수
	 */
	long countLedger(UUID userId, TicketLedgerFilter filter);
}
