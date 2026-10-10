package com.getddo.core.attendance.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 한 달에 적용하는 연속 출석 정책 묶음. 대상 월 이하에서 가장 최근에 시작한 묶음을 쓰며, 그 묶음의 단계만 사용한다.
 */
@Getter
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
	 * 출석 날짜에서 각 단계에 처음 도달한 날짜를 찾는다. 단계 일수 이상 이어진 첫 연속 구간의 그 일수째 날이다.
	 * 연속이 끊긴 뒤 다시 도달해도 이미 도달한 단계는 처음 도달한 날짜 하나만 돌려준다.
	 *
	 * @param sortedDates 그 달의 출석 KST 날짜(오름차순)
	 * @return 도달한 단계, 단계 일수 오름차순. 도달한 단계가 없으면 빈 목록
	 */
	public List<ReachedMilestone> reachedBy(List<LocalDate> sortedDates) {
		List<ConsecutiveRuns.Run> runs = ConsecutiveRuns.of(sortedDates);
		List<ReachedMilestone> reached = new ArrayList<>();
		for (StreakMilestone milestone : milestones) {
			runs.stream()
					.filter(run -> run.length() >= milestone.getMilestoneDays())
					.findFirst()
					.ifPresent(run -> reached.add(new ReachedMilestone(milestone,
							run.start().plusDays(milestone.getMilestoneDays() - 1L))));
		}
		return List.copyOf(reached);
	}
}
