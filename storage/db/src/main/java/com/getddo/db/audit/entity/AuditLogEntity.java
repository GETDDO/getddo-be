package com.getddo.db.audit.entity;

import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.getddo.db.common.entity.BaseEntity;

/** 기존 audit_logs에 추가하는 변경 이력. JPA 변경 감지로 과거 기록을 수정하지 않는다. */
@Entity
@Table(name = "audit_logs")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntity extends BaseEntity {

	// 사용자 객체를 탐색하지 않으며 유효성은 기존 users FK로 보장한다. 시스템 처리는 null이다.
	@Column(length = 16)
	private UUID actorId;

	@Column(nullable = false, length = 60)
	private String action;

	@Column(nullable = false, length = 60)
	private String targetType;

	@Column(nullable = false, length = 16)
	private UUID targetId;

	@Column(columnDefinition = "text")
	private String reason;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "json")
	private Map<String, Object> beforeData;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "json")
	private Map<String, Object> afterData;

	@Column(length = 100)
	private String requestId;

	public AuditLogEntity(UUID actorId, String action, String targetType, UUID targetId,
			String reason, Map<String, Object> beforeData, Map<String, Object> afterData, String requestId) {
		this.actorId = actorId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reason = reason;
		this.beforeData = beforeData;
		this.afterData = afterData;
		this.requestId = requestId;
	}
}
