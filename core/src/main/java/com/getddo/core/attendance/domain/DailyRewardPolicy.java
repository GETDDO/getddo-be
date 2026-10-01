package com.getddo.core.attendance.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 출석 시각에 적용하는 일일 출석 보상 정책({@code reward_policies}의 ATTENDANCE 정책). */
@Getter
@EqualsAndHashCode
@ToString
public final class DailyRewardPolicy {

	private final UUID id;
	/** 일일 지급 수량. 1 이상. */
	private final int rewardTicketCount;

	public DailyRewardPolicy(UUID id, int rewardTicketCount) {
		this.id = Objects.requireNonNull(id, "id");
		this.rewardTicketCount = rewardTicketCount;
	}
}
