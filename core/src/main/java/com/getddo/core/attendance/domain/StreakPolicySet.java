package com.getddo.core.attendance.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 한 달에 적용하는 연속 출석 정책 묶음. 대상 월 이하에서 가장 최근에 시작한 묶음을 쓰며, 그 묶음의 단계만 사용한다.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class StreakPolicySet {

	private final UUID id;
	/** 단계 일수 오름차순의 변경 불가능한 목록. */
	private final List<StreakMilestone> milestones;

	public StreakPolicySet(UUID id, List<StreakMilestone> milestones) {
		this.id = Objects.requireNonNull(id, "id");
		this.milestones = milestones.stream()
				.sorted(Comparator.comparingInt(StreakMilestone::getMilestoneDays))
				.toList();
	}

	/**
	 * 연속 일수가 정확히 도달한 단계를 찾는다. 연속 일수는 하루에 1씩 늘어나므로 도달한 날에만 해당한다.
	 *
	 * @return 도달한 단계. 없으면 빈 값
	 */
	public Optional<StreakMilestone> milestoneReachedAt(int consecutiveDays) {
		return milestones.stream().filter(milestone -> milestone.getMilestoneDays() == consecutiveDays).findFirst();
	}
}
