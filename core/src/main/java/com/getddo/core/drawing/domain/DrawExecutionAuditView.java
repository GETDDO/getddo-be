package com.getddo.core.drawing.domain;
import java.time.Instant;
import java.util.UUID;
/** 기존 추첨 실행 감사 이력의 읽기 모델. 원본 감사 행을 변경하지 않는다. */
public record DrawExecutionAuditView(UUID id, UUID actorId, String action, String reason, String beforeData,
		String afterData, String requestId, Instant createdAt) { }
