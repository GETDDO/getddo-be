package com.getddo.api.drawing.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawVerification;

public record DrawVerificationResponse(UUID id, UUID drawRunId, boolean passed, List<Item> checks,
		Instant verifiedAt, UUID verifiedBy) {
	public DrawVerificationResponse {
		checks = List.copyOf(checks);
	}
	public static DrawVerificationResponse from(DrawVerification verification) {
		return new DrawVerificationResponse(verification.getId(), verification.getDrawRunId(), verification.isPassed(),
				verification.getChecks().stream().map(item -> new Item(item.getCode(), item.isPassed(),
				item.getMessage())).toList(),
				verification.getVerifiedAt(), verification.getVerifiedBy());
	}
	public record Item(String code, boolean passed, String message) {
	}
}
