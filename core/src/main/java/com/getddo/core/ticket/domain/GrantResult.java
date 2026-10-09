package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.List;

import lombok.Getter;

/**
 * 확정된 응모권 지급 결과.
 *
 * <p>{@code quantity}·{@code grantedAt}·{@code expiresAt}은 API 응답의 {@code RewardReceipt}
 * ({@code ticketCount}, {@code grantedAt}, {@code expiresAt})에 그대로 매핑된다. 한 지급 건의 응모권은 모두
 * 같은 등급과 같은 만료 시각을 가진다.</p>
 */
@Getter
public final class GrantResult {

	/** 이번 지급 건으로 발급된 응모권 장수. */
	private final long quantity;
	private final TicketGrade grade;
	/** 지급 시각 UTC. 지급 이력의 처리 시각과 같다. */
	private final Instant grantedAt;
	/** 지급 당시의 만료 시각 UTC. */
	private final Instant expiresAt;
	/** true면 이번 호출이 아니라 이전에 확정된 지급을 돌려준 것이다. */
	private final boolean replayed;

	public GrantResult(long quantity, TicketGrade grade, Instant grantedAt, Instant expiresAt, boolean replayed) {
		this.quantity = quantity;
		this.grade = grade;
		this.grantedAt = grantedAt;
		this.expiresAt = expiresAt;
		this.replayed = replayed;
	}

	/**
	 * 이미 지급된 응모권으로 재요청 결과를 만든다.
	 *
	 * @param granted 한 지급 건의 응모권. 비어 있으면 안 된다
	 * @throws IllegalArgumentException 목록이 비어 있는 경우
	 * @throws IllegalStateException 응모권끼리 등급·지급 시각·만료 시각이 다른 경우
	 */
	public static GrantResult replayOf(List<GrantedTicket> granted) {
		if (granted == null || granted.isEmpty()) {
			throw new IllegalArgumentException("재요청 결과를 만들 응모권이 없다.");
		}
		GrantedTicket first = granted.get(0);
		boolean uniform = granted.stream().allMatch(ticket -> ticket.getGrade() == first.getGrade()
				&& ticket.getGrantedAt().equals(first.getGrantedAt())
				&& ticket.getExpiresAt().equals(first.getExpiresAt()));
		if (!uniform) {
			throw new IllegalStateException("한 지급 건의 응모권은 등급·지급 시각·만료 시각이 모두 같아야 한다.");
		}
		return new GrantResult(granted.size(), first.getGrade(), first.getGrantedAt(), first.getExpiresAt(), true);
	}
}
