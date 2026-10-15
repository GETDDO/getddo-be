package com.getddo.api.event.dto.request;

import jakarta.validation.constraints.NotBlank;

/** AE08 취소 사유. 저장 가능한 UTF-8 바이트 상한은 서비스에서도 검증한다. */
public record EventCancellationRequest(@NotBlank String reason) {
}
