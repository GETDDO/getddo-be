package com.getddo.core.mission.domain;

import java.util.Objects;

import lombok.Getter;

/**
 * 사용자 목록에 보여 주는 미션 한 건이다. 문항 없이 미션과, 요청한 사용자가 이미 완료했는지만 담는다.
 */
@Getter
public final class MissionSummary {

	private final Mission mission;
	private final boolean completed;

	/**
	 * @param mission   문항 없는 미션
	 * @param completed 요청한 사용자가 이 미션을 완료했는지 여부
	 */
	public MissionSummary(Mission mission, boolean completed) {
		this.mission = Objects.requireNonNull(mission, "mission");
		this.completed = completed;
	}
}
