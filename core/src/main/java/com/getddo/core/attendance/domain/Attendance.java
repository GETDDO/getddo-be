package com.getddo.core.attendance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 사용자의 KST 업무일 출석 기록. 같은 사용자·같은 날짜에는 하나만 존재한다. */
@Getter
@EqualsAndHashCode
public final class Attendance {

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID userId;
	/** 출석 기준 KST 날짜. */
	private final LocalDate attendanceDate;
	/** 기록 시각 UTC. 저장 전에는 null. */
	private final Instant createdAt;

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
}
