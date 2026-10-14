package com.getddo.core.ticket.domain;

/** 지급 건의 응모권 등급을 정한다. 한 지급 건에 대해 한 번만 호출한다. */
public interface TicketGradeDrawer {

	/**
	 * 지급 근거 종류에 맞는 등급을 정한다.
	 *
	 * <p>출석은 브론즈로 고정한다. 미션·게임은 브론즈 80%, 실버 18%, 골드 2% 확률로 무작위 결정한다.</p>
	 *
	 * @param type 지급 근거 종류
	 * @return 확정할 등급
	 */
	TicketGrade draw(GrantSourceType type);
}
