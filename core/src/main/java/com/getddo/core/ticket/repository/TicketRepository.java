package com.getddo.core.ticket.repository;

import java.util.List;

import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantedTicket;
import com.getddo.core.ticket.domain.Ticket;

/** 응모권 저장소. 모든 메서드는 호출자 트랜잭션 안에서 호출한다. */
public interface TicketRepository {

	/**
	 * 이 청구로 이미 지급된 응모권을 지급 당시 값으로 조회한다.
	 *
	 * @param source 지급 근거 청구
	 * @return 지급된 응모권. 아직 지급되지 않았으면 빈 목록
	 */
	List<GrantedTicket> findGranted(GrantSource source);

	/**
	 * 새 응모권을 저장한다.
	 *
	 * @param tickets ID가 없는 응모권
	 * @return ID가 발급된 응모권. 입력과 같은 순서다
	 */
	List<Ticket> saveAll(List<Ticket> tickets);
}
