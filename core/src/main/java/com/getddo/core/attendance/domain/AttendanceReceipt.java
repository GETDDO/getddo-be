package com.getddo.core.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 출석 처리 결과(AT02). API 응답의 {@code AttendanceReceipt}에 대응한다.
 *
 * <p>같은 날 다시 요청하면 이미 확정한 출석과 보상을 그대로 돌려주며 {@code created}가 false다.
 * 이때 {@code rewards}는 이번 호출에서 새로 지급했다는 뜻이 아니다.</p>
 */
public final class AttendanceReceipt {

	private final UUID attendanceId;
	private final LocalDate attendanceDate;
	private final int consecutiveDays;
	private final List<AttendanceRewardReceipt> rewards;
	private final Instant createdAt;
	private final boolean created;

	/**
	 * @param attendanceId    출석 ID
	 * @param attendanceDate  출석 기준 KST 날짜
	 * @param consecutiveDays 이 출석까지 같은 달의 실제 연속 출석 일수(최대 31)
	 * @param rewards         이 출석으로 확정된 보상. 일일 보상이 먼저, 단계 보상은 단계 일수 오름차순
	 * @param createdAt       출석 기록 시각 UTC
	 * @param created         이번 호출에서 새로 출석했으면 true, 이미 출석한 날의 재요청이면 false
	 */
	public AttendanceReceipt(UUID attendanceId, LocalDate attendanceDate, int consecutiveDays,
			List<AttendanceRewardReceipt> rewards, Instant createdAt, boolean created) {
		this.attendanceId = Objects.requireNonNull(attendanceId, "attendanceId");
		this.attendanceDate = Objects.requireNonNull(attendanceDate, "attendanceDate");
		this.consecutiveDays = consecutiveDays;
		this.rewards = List.copyOf(rewards);
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.created = created;
	}

	public UUID getAttendanceId() {
		return attendanceId;
	}

	public LocalDate getAttendanceDate() {
		return attendanceDate;
	}

	public int getConsecutiveDays() {
		return consecutiveDays;
	}

	/** 이 출석으로 확정된 보상의 변경 불가능한 목록. */
	public List<AttendanceRewardReceipt> getRewards() {
		return rewards;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	/** 이번 호출에서 새로 출석했는지. API에서 201과 200을 구분하는 데 쓴다. */
	public boolean isCreated() {
		return created;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AttendanceReceipt that)) {
			return false;
		}
		return consecutiveDays == that.consecutiveDays
				&& created == that.created
				&& attendanceId.equals(that.attendanceId)
				&& attendanceDate.equals(that.attendanceDate)
				&& rewards.equals(that.rewards)
				&& createdAt.equals(that.createdAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(attendanceId, attendanceDate, consecutiveDays, rewards, createdAt, created);
	}

	@Override
	public String toString() {
		return "AttendanceReceipt[attendanceId=" + attendanceId + ", attendanceDate=" + attendanceDate
				+ ", consecutiveDays=" + consecutiveDays + ", rewards=" + rewards + ", created=" + created + "]";
	}
}
