package com.getddo.core.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** 사용자의 KST 업무일 출석 기록. 같은 사용자·같은 날짜에는 하나만 존재한다. */
public final class Attendance {

	private final UUID id;
	private final UUID userId;
	private final LocalDate attendanceDate;
	private final Instant createdAt;

	/**
	 * @param id             출석 ID. 저장 전에는 null
	 * @param userId         사용자 ID
	 * @param attendanceDate 출석 기준 KST 날짜
	 * @param createdAt      기록 시각 UTC. 저장 전에는 null
	 */
	public Attendance(UUID id, UUID userId, LocalDate attendanceDate, Instant createdAt) {
		this.id = id;
		this.userId = Objects.requireNonNull(userId, "userId");
		this.attendanceDate = Objects.requireNonNull(attendanceDate, "attendanceDate");
		this.createdAt = createdAt;
	}

	/** 저장 전 출석 기록을 만든다. */
	public static Attendance create(UUID userId, LocalDate attendanceDate) {
		return new Attendance(null, userId, attendanceDate, null);
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public LocalDate getAttendanceDate() {
		return attendanceDate;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof Attendance that)) {
			return false;
		}
		return Objects.equals(id, that.id) && userId.equals(that.userId)
				&& attendanceDate.equals(that.attendanceDate) && Objects.equals(createdAt, that.createdAt);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, userId, attendanceDate, createdAt);
	}

	@Override
	public String toString() {
		return "Attendance[id=" + id + ", userId=" + userId + ", attendanceDate=" + attendanceDate + "]";
	}
}
