package com.getddo.core.attendance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 관리자가 등록한 일일 출석 보상 정책 한 건. 적용 기간은 {@code [effectiveFrom, effectiveUntil)}이다. */
@Getter
public final class DailyAttendancePolicy {

	private final UUID id;
	private final int rewardTicketCount;
	private final Instant effectiveFrom;
	/** 적용 종료 시각. 현재 적용 중이거나 예약된 마지막 정책은 {@code null}. */
	private final Instant effectiveUntil;
	private final UUID createdBy;
	private final Instant createdAt;

	public DailyAttendancePolicy(UUID id, int rewardTicketCount, Instant effectiveFrom, Instant effectiveUntil,
			UUID createdBy, Instant createdAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.rewardTicketCount = rewardTicketCount;
		this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
		this.effectiveUntil = effectiveUntil;
		this.createdBy = Objects.requireNonNull(createdBy, "createdBy");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}
}
