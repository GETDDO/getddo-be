package com.getddo.core.ticket.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.ticket.domain.TicketHistoryCursor;
import com.getddo.core.ticket.domain.TicketHistoryFilter;
import com.getddo.core.ticket.domain.TicketHistoryView;
import com.getddo.core.ticket.domain.TicketHolding;

/** 사용자 화면용 응모권 조회. 응모권과 이력을 읽기만 한다. */
public interface TicketQueryRepository {

	/**
	 * 사용자의 사용 가능한 응모권을 등급·만료 시각별로 묶어 조회한다.
	 *
	 * <p>상태가 사용 가능이고 만료 시각이 {@code now}보다 뒤인 응모권만 센다. 만료 처리가 늦어 아직 사용 가능으로
	 * 저장된 응모권도 만료 시각이 지났으면 제외한다.</p>
	 *
	 * @param userId 사용자 ID
	 * @param now    판정 기준 시각
	 * @return 만료가 임박한 순서의 묶음. 없으면 빈 목록
	 */
	List<TicketHolding> findHoldings(UUID userId, Instant now);

	/**
	 * 사용자의 이력을 {@code createdAt DESC, id DESC} 순서로 조회한다.
	 *
	 * @param userId 사용자 ID
	 * @param filter 조회 조건
	 * @param after  이 커서보다 뒤의 행부터 조회한다. 첫 조회면 null
	 * @param limit  최대 행 수
	 * @return 파생 값을 채운 이력 목록
	 */
	List<TicketHistoryView> findHistory(UUID userId, TicketHistoryFilter filter, TicketHistoryCursor after,
			int limit);

	/**
	 * 커서와 관계없이 조회 조건에 맞는 전체 이력 수를 센다.
	 *
	 * @param userId 사용자 ID
	 * @param filter 조회 조건
	 * @return 전체 이력 수
	 */
	long countHistory(UUID userId, TicketHistoryFilter filter);
}
