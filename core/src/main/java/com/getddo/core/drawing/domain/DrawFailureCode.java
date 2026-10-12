package com.getddo.core.drawing.domain;

/** 저장·응답에는 코드만 사용하며 예외 원문을 포함하지 않는다. */
public enum DrawFailureCode {
	TRANSIENT_STORAGE(true), INTERRUPTED(true), INVALID_EVIDENCE(false), INVALID_RUN(false), NOT_EXECUTABLE(false), INTERNAL_ERROR(false);
	private final boolean retryable;
	DrawFailureCode(boolean retryable) { this.retryable = retryable; }
	public boolean isRetryable() { return retryable; }
}
