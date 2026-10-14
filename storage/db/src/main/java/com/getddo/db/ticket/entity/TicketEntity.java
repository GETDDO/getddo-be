package com.getddo.db.ticket.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketStatus;

/**
 * 응모권 한 장의 현재 상태 Entity.
 *
 * <p><b>{@code BaseEntity}를 상속하지 않는 이유.</b> 공통 Entity의 {@code @CreatedDate}는 영속화 시점에 시계를
 * 다시 읽어 서비스가 구한 지급 시각을 덮어쓴다. 응모권의 {@code created_at}은 만료 시각을 계산한 지급 시각이자 지급 이력의
 * 처리 시각과 같아야 하므로 직접 채운다(ADR-0001의 예외, 응모권 Entity에 한정). ID는 공통 Entity와 같은 UUID v7이다.</p>
 *
 * <p>사용자·지급 근거·등급·생성 시각은 지급 후 바뀌지 않으므로 갱신 불가로 매핑한다. 상태·만료 시각·버전·수정 시각은
 * 사용·반환·만료 처리가 바꾸며, 바뀔 때마다 이력이 한 건씩 쌓인다. 사용자·청구 참조는 다른 도메인 Entity와의
 * 연관관계 없이 UUID 값으로만 둔다.</p>
 */
@Getter
@Entity
@Table(name = "tickets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketEntity {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(name = "id", nullable = false, updatable = false, length = 16)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Column(name = "attendance_reward_claim_id", updatable = false, length = 16)
	private UUID attendanceRewardClaimId;

	@Column(name = "mission_reward_claim_id", updatable = false, length = 16)
	private UUID missionRewardClaimId;

	@Column(name = "game_reward_claim_id", updatable = false, length = 16)
	private UUID gameRewardClaimId;

	@Enumerated(EnumType.STRING)
	@Column(name = "grade", nullable = false, updatable = false)
	private TicketGrade grade;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private TicketStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Builder
	private TicketEntity(UUID userId, UUID attendanceRewardClaimId, UUID missionRewardClaimId,
			UUID gameRewardClaimId, TicketGrade grade, TicketStatus status, Instant expiresAt, long version,
			Instant createdAt, Instant updatedAt) {
		this.userId = userId;
		this.attendanceRewardClaimId = attendanceRewardClaimId;
		this.missionRewardClaimId = missionRewardClaimId;
		this.gameRewardClaimId = gameRewardClaimId;
		this.grade = grade;
		this.status = status;
		this.expiresAt = expiresAt;
		this.version = version;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	/** 사용·반환·만료 처리가 바꾸는 값만 한꺼번에 반영한다. 등급·소유자·청구·생성 시각은 바꾸지 않는다. */
	public void applyTransition(TicketStatus status, Instant expiresAt, long version, Instant updatedAt) {
		this.status = status;
		this.expiresAt = expiresAt;
		this.version = version;
		this.updatedAt = updatedAt;
	}
}
