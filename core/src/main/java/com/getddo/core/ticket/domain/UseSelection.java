package com.getddo.core.ticket.domain;

import java.util.Objects;

import lombok.Getter;

/**
 * 사용자가 응모에 쓰려고 고른 등급과 장수. 보유 조회(T01)의 등급별 보유 장수({@code countByGrade}) 안에서 정한다.
 *
 * <p>같은 등급 안에서는 만료가 이른 응모권부터 쓴다. 사용자가 늦게 만료하는 응모권을 남겨 두는 편이 항상 유리하고,
 * 추첨 가중치는 등급으로만 정해지므로 어느 응모권을 쓰는지는 사용자의 선택에 영향을 주지 않는다.</p>
 */
@Getter
public final class UseSelection {

	private final TicketGrade grade;
	/** 이 등급에서 쓸 장수. 서비스가 1 이상이고 보유 장수 이하인지 확인한다. */
	private final long count;

	public UseSelection(TicketGrade grade, long count) {
		Objects.requireNonNull(grade, "grade");
		this.grade = grade;
		this.count = count;
	}
}
