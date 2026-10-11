package com.getddo.core.attendance.domain;

import java.time.LocalDate;
import java.util.Objects;

import lombok.Getter;

/** 출석 기록에서 계산한, 연속 출석 단계에 처음 도달한 날짜. */
@Getter
public final class ReachedMilestone {

	private final StreakMilestone milestone;
	/** 단계 일수에 처음 도달한 출석 KST 날짜. */
	private final LocalDate reachedDate;

	public ReachedMilestone(StreakMilestone milestone, LocalDate reachedDate) {
		this.milestone = Objects.requireNonNull(milestone, "milestone");
		this.reachedDate = Objects.requireNonNull(reachedDate, "reachedDate");
	}
}
