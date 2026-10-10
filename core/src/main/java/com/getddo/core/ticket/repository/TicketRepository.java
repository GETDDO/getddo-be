package com.getddo.core.ticket.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

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

	/**
	 * 사용자가 지금 쓸 수 있는 응모권을 만료 임박순, 같으면 낮은 등급 먼저, 그다음 ID순으로 최대 {@code limit}장 잠가 읽는다.
	 * 같은 사용자의 동시 차감은 같은 순서로 한 장씩 잠금을 잡으므로 서로 기다릴 뿐 교착하지 않는다. 잠금을 기다리는
	 * 동안 다른 처리가 쓰거나 만료시킨 응모권은 잠근 뒤 최신 값으로 판정해 건너뛰므로, 결과가 {@code limit}보다 적으면
	 * 쓸 수 있는 응모권이 부족한 것이다.
	 *
	 * @param now 이 시각 이후에 만료하는 응모권만 읽는다
	 * @param limit 최대 개수
	 */
	List<Ticket> findUsableForUpdate(UUID userId, Instant now, int limit);

	/** 응모권을 ID 오름차순으로 잠가 읽는다. 없는 ID는 결과에서 빠진다. */
	List<Ticket> findAllForUpdate(Collection<UUID> ids);

	/** 응모권을 잠그지 않고 읽는다. 없는 ID는 결과에서 빠진다. */
	List<Ticket> findAllByIds(Collection<UUID> ids);

	/**
	 * 저장된 응모권의 상태·만료 시각·버전·수정 시각을 바꾼다. 바뀌지 않는 값은 건드리지 않는다.
	 *
	 * @param tickets ID가 있는 응모권
	 */
	void updateAll(List<Ticket> tickets);
}
