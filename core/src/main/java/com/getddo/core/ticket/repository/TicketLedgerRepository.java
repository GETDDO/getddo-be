package com.getddo.core.ticket.repository;

import java.util.Optional;

import com.getddo.core.ticket.domain.TicketLedger;

/** 응모권 원장 저장소. 원장은 추가만 하며 수정·삭제하지 않는다. */
public interface TicketLedgerRepository {

	/**
	 * 멱등키로 원장 행을 조회한다.
	 *
	 * @param idempotencyKey 원장 행별 고정 키
	 * @return 원장 행. 없으면 빈 값
	 */
	Optional<TicketLedger> findByIdempotencyKey(String idempotencyKey);

	/**
	 * 새 원장 행을 추가한다.
	 *
	 * @param ledger ID가 없는 원장 행
	 * @return ID가 발급된 원장 행
	 */
	TicketLedger save(TicketLedger ledger);
}
