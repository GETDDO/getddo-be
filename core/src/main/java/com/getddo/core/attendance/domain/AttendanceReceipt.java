package com.getddo.core.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 출석 처리 결과(AT02). API 응답의 {@code AttendanceReceipt}에 대응한다.
 *
 * <p>같은 날 다시 요청하면 이미 확정한 출석과 보상을 그대로 돌려주며 {@code created}가 false다.
 * 이때 {@code rewards}는 이번 호출에서 새로 지급했다는 뜻이 아니다.</p>
 */
@Getter
@EqualsAndHashCode
@ToString
public final class AttendanceReceipt {

	private final UUID attendanceId;
	/** 출석 기준 KST 날짜. */
	private final LocalDate attendanceDate;
	/** 이 출석까지 같은 달의 실제 연속 출석 일수(최대 31). */
	private final int consecutiveDays;
	/** 일일 보상이 먼저, 단계 보상은 단계 일수 오름차순인 변경 불가능한 목록. */
	private final List<AttendanceRewardReceipt> rewards;
	/** 출석 기록 시각 UTC. */
	private final Instant createdAt;
	/** 이번 호출에서 새로 출석했는지. API에서 201과 200을 구분하는 데 쓴다. */
	private final boolean created;

	public AttendanceReceipt(UUID attendanceId, LocalDate attendanceDate, int consecutiveDays,
			List<AttendanceRewardReceipt> rewards, Instant createdAt, boolean created) {
		this.attendanceId = Objects.requireNonNull(attendanceId, "attendanceId");
		this.attendanceDate = Objects.requireNonNull(attendanceDate, "attendanceDate");
		this.consecutiveDays = consecutiveDays;
		this.rewards = List.copyOf(rewards);
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.created = created;
	}
}
