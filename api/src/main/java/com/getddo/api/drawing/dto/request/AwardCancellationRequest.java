package com.getddo.api.drawing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AD05 관리자 취소 사유. 선택적 어뷰징 연결은 이번 계약에 포함하지 않는다. */
public record AwardCancellationRequest(
		@NotBlank(message = "취소 사유가 필요합니다.")
		@Size(max = 1000, message = "취소 사유는 1000자 이하여야 합니다.") String reason) {
	/** 양끝 공백을 제거한 사유를 검증·저장·멱등 비교에 동일하게 사용한다. */
	public AwardCancellationRequest {
		if (reason != null) reason = reason.strip();
	}
}
