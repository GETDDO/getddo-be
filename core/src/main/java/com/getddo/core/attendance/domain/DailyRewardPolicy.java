package com.getddo.core.attendance.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 출석 시각에 적용하는 일일 출석 보상 정책({@code reward_policies}의 ATTENDANCE 정책). */
@Getter
@EqualsAndHashCode
public final class DailyRewardPolicy {

	private final UUID id;
	/** 일일 지급 수량. 1 이상. */
	private final int rewardTicketCount;

	/**
	 * @throws IllegalArgumentException 지급 수량이 1 미만인 경우. {@code chk_reward_policy_quantity}와 같은 범위다
	 */
	public DailyRewardPolicy(UUID id, int rewardTicketCount) {
		if (rewardTicketCount < 1) {
			throw new IllegalArgumentException("일일 출석 보상 수량은 1 이상이어야 한다.");
		}
		this.id = Objects.requireNonNull(id, "id");
		this.rewardTicketCount = rewardTicketCount;
	}
}
