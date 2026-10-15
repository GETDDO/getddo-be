package com.getddo.api.common.ratelimit;

import com.getddo.api.common.exception.CommonErrorCode;
import com.getddo.core.common.exception.BusinessException;

/** 요청 빈도 한도를 넘었다. 응답은 429이며 {@code Retry-After} 헤더로 다시 시도할 수 있는 때를 알려 준다. */
public class RateLimitExceededException extends BusinessException {

	private final long retryAfterSeconds;

	public RateLimitExceededException(long retryAfterSeconds) {
		super(CommonErrorCode.RATE_LIMIT_EXCEEDED);
		this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
	}

	/** 다시 시도하기까지 남은 초(1 이상). */
	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
