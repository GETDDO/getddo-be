package com.getddo.core.ticket.repository;

import java.util.List;
import java.util.UUID;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.ticket.domain.AdminTicketHistoryFilter;
import com.getddo.core.ticket.domain.AdminTicketHistoryView;
import com.getddo.core.ticket.domain.TicketGrantFilter;
import com.getddo.core.ticket.domain.TicketInconsistencyView;
import com.getddo.core.ticket.domain.TicketGrantView;
import com.getddo.core.ticket.domain.TicketRefundFilter;
import com.getddo.core.ticket.domain.TicketRefundView;

/** 관리자 화면용 응모권 조회. 응모권과 이력, 이를 설명하는 다른 도메인의 이름을 읽기만 한다. */
public interface AdminTicketQueryRepository {

	/**
	 * 응모권 지급 이력을 지급 시각 최신순({@code grantedAt DESC, historyId DESC})으로 조회한다.
	 *
	 * @param filter 조회 조건
	 * @param page   페이지 번호(1부터)와 크기
	 * @return 한 페이지와 조건에 맞는 전체 건수
	 */
	PageResult<TicketGrantView> findGrants(TicketGrantFilter filter, PageQuery page);

	/**
	 * 응모권 처리 이력을 처리 시각 최신순({@code createdAt DESC, id DESC})으로 조회한다.
	 *
	 * @param filter 조회 조건
	 * @param page   페이지 번호(1부터)와 크기
	 * @return 한 페이지와 조건에 맞는 전체 건수
	 */
	PageResult<AdminTicketHistoryView> findHistories(AdminTicketHistoryFilter filter, PageQuery page);

	/**
	 * 응모권 반환 이력을 반환 시각 최신순({@code refundedAt DESC, id DESC})으로 조회한다.
	 *
	 * @param filter 조회 조건
	 * @param page   페이지 번호(1부터)와 크기
	 * @return 한 페이지와 조건에 맞는 전체 건수
	 */
	PageResult<TicketRefundView> findRefunds(TicketRefundFilter filter, PageQuery page);

	/** 사용자가 있는지 확인한다. */
	boolean userExists(UUID userId);

	/**
	 * 한 사용자의 어긋난 응모권을 모두 조회한다. 현재 버전·상태가 마지막 이력의 버전·상태와 다른 응모권이다.
	 *
	 * @param userId 사용자 ID
	 * @return 어긋난 응모권. 없으면 빈 목록
	 */
	List<TicketInconsistencyView> findInconsistenciesOf(UUID userId);

	/**
	 * 어긋난 응모권을 한 페이지 조회한다.
	 *
	 * @param userId 사용자로 좁히려면 사용자 ID, 전체면 null
	 * @param page   페이지 번호(1부터)와 크기
	 * @return 한 페이지와 어긋난 응모권 전체 수
	 */
	PageResult<TicketInconsistencyView> findInconsistencies(UUID userId, PageQuery page);
}
