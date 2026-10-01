package com.getddo.core.attendance.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * 출석 보상 청구. 응모권 지급의 근거가 되며, 청구 ID로 응모권을 지급한다.
 *
 * <p>{@code sourceKey}는 사용자·보상 종류와 함께 UNIQUE라 같은 보상을 두 번 청구하지 못하게 한다.
 * 일일 보상은 출석 날짜({@code 2026-09-30}), 단계 보상은 KST 기준월과 단계 일수({@code 2026-09:7})다.
 * 그래서 단계 보상은 연속이 끊겼다가 같은 달에 같은 일수에 다시 도달해도 한 번만 청구된다.</p>
 */
public final class AttendanceRewardClaim {

	private final UUID id;
	private final UUID userId;
	private final UUID attendanceId;
	private final AttendanceRewardType rewardType;
	private final UUID rewardPolicyId;
	private final UUID streakPolicyId;
	private final LocalDate rewardDate;
	private final Integer milestoneDays;
	private final String sourceKey;
	private final int ticketCount;

	/**
	 * @param id             청구 ID. 저장 전에는 null
	 * @param userId         사용자 ID
	 * @param attendanceId   근거 출석 ID
	 * @param rewardType     보상 종류
	 * @param rewardPolicyId 일일 보상 정책 ID. 단계 보상이면 null
	 * @param streakPolicyId 단계 정책 ID. 일일 보상이면 null
	 * @param rewardDate     보상 기준 KST 날짜
	 * @param milestoneDays  단계 일수. 일일 보상이면 null
	 * @param sourceKey      중복 방지 키
	 * @param ticketCount    지급 수량
	 */
	public AttendanceRewardClaim(UUID id, UUID userId, UUID attendanceId, AttendanceRewardType rewardType,
			UUID rewardPolicyId, UUID streakPolicyId, LocalDate rewardDate, Integer milestoneDays, String sourceKey,
			int ticketCount) {
		this.id = id;
		this.userId = Objects.requireNonNull(userId, "userId");
		this.attendanceId = Objects.requireNonNull(attendanceId, "attendanceId");
		this.rewardType = Objects.requireNonNull(rewardType, "rewardType");
		this.rewardPolicyId = rewardPolicyId;
		this.streakPolicyId = streakPolicyId;
		this.rewardDate = Objects.requireNonNull(rewardDate, "rewardDate");
		this.milestoneDays = milestoneDays;
		this.sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
		this.ticketCount = ticketCount;
	}

	/** 저장된 출석의 일일 보상 청구를 만든다. */
	public static AttendanceRewardClaim daily(Attendance attendance, DailyRewardPolicy policy) {
		LocalDate date = attendance.getAttendanceDate();
		return new AttendanceRewardClaim(null, attendance.getUserId(), requireId(attendance), AttendanceRewardType.DAILY,
				policy.getId(), null, date, null, dailySourceKey(date), policy.getRewardTicketCount());
	}

	/** 저장된 출석으로 도달한 단계의 보상 청구를 만든다. */
	public static AttendanceRewardClaim streak(Attendance attendance, StreakMilestone milestone) {
		LocalDate date = attendance.getAttendanceDate();
		return new AttendanceRewardClaim(null, attendance.getUserId(), requireId(attendance), AttendanceRewardType.STREAK,
				null, milestone.getId(), date, milestone.getMilestoneDays(),
				streakSourceKey(date, milestone.getMilestoneDays()), milestone.getRewardTicketCount());
	}

	/** 일일 보상의 중복 방지 키. 출석 KST 날짜 {@code yyyy-MM-dd}. */
	public static String dailySourceKey(LocalDate attendanceDate) {
		return attendanceDate.toString();
	}

	/** 단계 보상의 중복 방지 키. KST 기준월과 단계 일수 {@code yyyy-MM:일수}. */
	public static String streakSourceKey(LocalDate attendanceDate, int milestoneDays) {
		return YearMonth.from(attendanceDate) + ":" + milestoneDays;
	}

	private static UUID requireId(Attendance attendance) {
		return Objects.requireNonNull(attendance.getId(), "attendance.id");
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public UUID getAttendanceId() {
		return attendanceId;
	}

	public AttendanceRewardType getRewardType() {
		return rewardType;
	}

	public UUID getRewardPolicyId() {
		return rewardPolicyId;
	}

	public UUID getStreakPolicyId() {
		return streakPolicyId;
	}

	public LocalDate getRewardDate() {
		return rewardDate;
	}

	public Integer getMilestoneDays() {
		return milestoneDays;
	}

	public String getSourceKey() {
		return sourceKey;
	}

	public int getTicketCount() {
		return ticketCount;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AttendanceRewardClaim that)) {
			return false;
		}
		return ticketCount == that.ticketCount && Objects.equals(id, that.id) && userId.equals(that.userId)
				&& attendanceId.equals(that.attendanceId) && rewardType == that.rewardType
				&& Objects.equals(rewardPolicyId, that.rewardPolicyId)
				&& Objects.equals(streakPolicyId, that.streakPolicyId) && rewardDate.equals(that.rewardDate)
				&& Objects.equals(milestoneDays, that.milestoneDays) && sourceKey.equals(that.sourceKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, userId, attendanceId, rewardType, rewardPolicyId, streakPolicyId, rewardDate,
				milestoneDays, sourceKey, ticketCount);
	}

	@Override
	public String toString() {
		return "AttendanceRewardClaim[id=" + id + ", rewardType=" + rewardType + ", sourceKey=" + sourceKey
				+ ", ticketCount=" + ticketCount + "]";
	}
}
