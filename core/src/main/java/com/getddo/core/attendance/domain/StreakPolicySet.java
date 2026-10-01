package com.getddo.core.attendance.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 한 달에 적용하는 연속 출석 정책 묶음. 대상 월 이하에서 가장 최근에 시작한 묶음을 쓰며, 그 묶음의 단계만 사용한다.
 */
public final class StreakPolicySet {

	private final UUID id;
	private final List<StreakMilestone> milestones;

	/**
	 * @param id         정책 묶음 ID
	 * @param milestones 이 묶음의 단계. 단계 일수 오름차순으로 보관한다
	 */
	public StreakPolicySet(UUID id, List<StreakMilestone> milestones) {
		this.id = Objects.requireNonNull(id, "id");
		this.milestones = milestones.stream()
				.sorted(Comparator.comparingInt(StreakMilestone::getMilestoneDays))
				.toList();
	}

	/**
	 * 연속 일수가 정확히 도달한 단계를 찾는다. 연속 일수는 하루에 1씩 늘어나므로 도달한 날에만 해당한다.
	 *
	 * @param consecutiveDays 이번 출석까지의 연속 일수
	 * @return 도달한 단계. 없으면 빈 값
	 */
	public Optional<StreakMilestone> milestoneReachedAt(int consecutiveDays) {
		return milestones.stream().filter(milestone -> milestone.getMilestoneDays() == consecutiveDays).findFirst();
	}

	public UUID getId() {
		return id;
	}

	/** 단계 일수 오름차순의 변경 불가능한 목록. */
	public List<StreakMilestone> getMilestones() {
		return milestones;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof StreakPolicySet that)) {
			return false;
		}
		return id.equals(that.id) && milestones.equals(that.milestones);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, milestones);
	}

	@Override
	public String toString() {
		return "StreakPolicySet[id=" + id + ", milestones=" + milestones + "]";
	}
}
