package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/**
 * 응모권 한 장의 현재 상태.
 *
 * <p>소유자·지급 근거·등급은 지급 때 정해지고 바뀌지 않는다. 상태와 만료 시각은 처리마다 바뀌며, 바뀔 때마다
 * {@link TicketHistory}가 한 건씩 쌓인다. 이 객체는 바꾸지 않고 새 객체를 만든다.</p>
 */
@Getter
public final class Ticket {

	/** 저장 전에는 null. */
	private final UUID id;
	private final UUID userId;
	/** 이 응모권을 최초로 지급한 보상 청구. */
	private final GrantSource grantSource;
	private final TicketGrade grade;
	private final TicketStatus status;
	private final Instant expiresAt;
	/** 최초 지급 1, 변경마다 1 증가. 마지막 이력의 버전과 같다. */
	private final long version;
	private final Instant createdAt;
	private final Instant updatedAt;

	public Ticket(UUID id, UUID userId, GrantSource grantSource, TicketGrade grade, TicketStatus status,
			Instant expiresAt, long version, Instant createdAt, Instant updatedAt) {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(grantSource, "grantSource");
		Objects.requireNonNull(grade, "grade");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(expiresAt, "expiresAt");
		Objects.requireNonNull(createdAt, "createdAt");
		Objects.requireNonNull(updatedAt, "updatedAt");
		if (version < 1) {
			throw new IllegalArgumentException("응모권 버전은 1 이상이어야 한다.");
		}
		if (updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("응모권 수정 시각은 생성 시각보다 빠를 수 없다.");
		}
		if (grantSource.getType() == GrantSourceType.ATTENDANCE && grade != TicketGrade.BRONZE) {
			throw new IllegalArgumentException("출석 보상은 브론즈 응모권만 지급한다.");
		}
		this.id = id;
		this.userId = userId;
		this.grantSource = grantSource;
		this.grade = grade;
		this.status = status;
		this.expiresAt = expiresAt;
		this.version = version;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/**
	 * 새로 지급하는 응모권을 만든다. 상태는 사용 가능, 버전은 1이다.
	 *
	 * @param issuedAt 실제 지급 시각. 생성·수정 시각에 같은 값을 쓴다
	 * @throws IllegalArgumentException 출석 보상에 브론즈가 아닌 등급을 주려는 경우
	 */
	public static Ticket issue(UUID userId, GrantSource grantSource, TicketGrade grade, Instant expiresAt,
			Instant issuedAt) {
		Objects.requireNonNull(issuedAt, "issuedAt");
		return new Ticket(null, userId, grantSource, grade, TicketStatus.AVAILABLE, expiresAt, 1, issuedAt, issuedAt);
	}

	/**
	 * 응모에 사용한 응모권을 만든다. 상태는 사용됨, 버전은 1 증가하고 만료 시각은 그대로다.
	 *
	 * @param usedAt 실제 사용 시각. 수정 시각이 된다
	 * @throws IllegalStateException 사용할 수 없는 상태이거나 이미 만료 시각이 지난 경우
	 */
	public Ticket use(Instant usedAt) {
		Objects.requireNonNull(usedAt, "usedAt");
		if (!status.isUsable()) {
			throw new IllegalStateException("사용할 수 있는 상태의 응모권이 아니다.");
		}
		if (!expiresAt.isAfter(usedAt)) {
			throw new IllegalStateException("만료 시각이 지난 응모권은 사용할 수 없다.");
		}
		return transitionedTo(TicketStatus.SPENT, expiresAt, usedAt);
	}

	/**
	 * 사용했던 응모권을 반환한 새 객체를 만든다. 상태는 반환됨, 버전은 1 증가하고 만료 시각은 새 값으로 바뀐다.
	 * 등급은 그대로다.
	 *
	 * @param refundedAt 실제 반환 시각. 수정 시각이 된다
	 * @param newExpiresAt 반환 후 만료 시각. 반환 시각보다 늦어야 한다
	 * @throws IllegalStateException 사용됨 상태가 아닌 경우
	 * @throws IllegalArgumentException 새 만료 시각이 반환 시각 이전인 경우
	 */
	public Ticket refund(Instant refundedAt, Instant newExpiresAt) {
		Objects.requireNonNull(refundedAt, "refundedAt");
		Objects.requireNonNull(newExpiresAt, "newExpiresAt");
		if (status != TicketStatus.SPENT) {
			throw new IllegalStateException("사용된 응모권만 반환할 수 있다.");
		}
		if (!newExpiresAt.isAfter(refundedAt)) {
			throw new IllegalArgumentException("반환 후 만료 시각은 반환 시각보다 늦어야 한다.");
		}
		return transitionedTo(TicketStatus.RETURNED, newExpiresAt, refundedAt);
	}

	private Ticket transitionedTo(TicketStatus nextStatus, Instant nextExpiresAt, Instant processedAt) {
		return new Ticket(id, userId, grantSource, grade, nextStatus, nextExpiresAt, version + 1, createdAt,
				processedAt);
	}
}
