package com.getddo.db.mission.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.springframework.data.domain.Persistable;

/**
 * 미션 제출 Entity. 추가만 하고 수정하지 않는다.
 *
 * <p>ID는 서버가 만들지 않고 클라이언트가 요청 전에 정한 UUID({@code Idempotency-Key})이고 {@code createdAt}은 서버가 제출을
 * 받은 시각이다. 직접 지정한 ID로 {@code save}를 부르면 Spring Data가 새 행인지 알 수 없어 {@code merge}(조회 뒤 저장)로
 * 가고, 같은 ID의 중복 제출이 조용히 통과할 수 있다. {@link Persistable}로 항상 새 행으로 보아 {@code INSERT}를 하게 하고, 중복이면
 * PK 위반 예외가 나게 한다.</p>
 */
@Getter
@Entity
@Immutable
@Table(name = "mission_submissions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MissionSubmissionEntity implements Persistable<UUID> {

	@Id
	@Column(name = "id", nullable = false, updatable = false, length = 16)
	private UUID id;

	@Column(name = "mission_id", nullable = false, updatable = false, length = 16)
	private UUID missionId;

	@Column(name = "reward_policy_id", nullable = false, updatable = false, length = 16)
	private UUID rewardPolicyId;

	@Column(name = "user_id", nullable = false, updatable = false, length = 16)
	private UUID userId;

	@Column(name = "is_completed", nullable = false, updatable = false)
	private boolean completed;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "request_hash", nullable = false, updatable = false, length = 64)
	private String requestHash;

	@Transient
	private boolean newEntity = true;

	public MissionSubmissionEntity(UUID id, UUID missionId, UUID rewardPolicyId, UUID userId, boolean completed,
			Instant createdAt, String requestHash) {
		this.id = id;
		this.missionId = missionId;
		this.rewardPolicyId = rewardPolicyId;
		this.userId = userId;
		this.completed = completed;
		this.createdAt = createdAt;
		this.requestHash = requestHash;
	}

	@Override
	public boolean isNew() {
		return newEntity;
	}

	@PostLoad
	@PostPersist
	private void markPersisted() {
		newEntity = false;
	}
}
