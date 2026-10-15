package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 관리자에게 보여 주는 응모권 지급 이력 한 건(AO03). 응모권 한 장의 지급 한 번이다.
 *
 * <p>{@code sourceId}는 출석 기록, 미션 제출, 게임 플레이의 ID이고 {@code missionId}는 미션 지급일 때만 채운다.</p>
 */
@Getter
public final class TicketGrantView {

	private final UUID historyId;
	private final UUID ticketId;
	private final UUID userId;
	private final String userName;
	private final TicketGrade grade;
	private final GrantSourceType sourceType;
	private final UUID sourceId;
	private final UUID missionId;
	private final Instant grantedAt;
	private final Instant expiresAt;

	public TicketGrantView(UUID historyId, UUID ticketId, UUID userId, String userName, TicketGrade grade,
			GrantSourceType sourceType, UUID sourceId, UUID missionId, Instant grantedAt, Instant expiresAt) {
		this.historyId = Objects.requireNonNull(historyId, "historyId");
		this.ticketId = Objects.requireNonNull(ticketId, "ticketId");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.userName = Objects.requireNonNull(userName, "userName");
		this.grade = Objects.requireNonNull(grade, "grade");
		this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
		this.sourceId = Objects.requireNonNull(sourceId, "sourceId");
		this.missionId = missionId;
		this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
	}
}
