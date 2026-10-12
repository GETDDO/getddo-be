package com.getddo.core.drawing.domain;
import java.time.Instant;
import java.util.UUID;
/** 실행 결과에 대한 취소 및 이 실행을 발생시킨 취소의 관리자 조회값. */
public record DrawCancellationView(UUID id, UUID resultId, UUID replacementRunId, UUID canceledBy, String reason, Instant canceledAt) { }
