package com.getddo.core.drawing.domain;

/** 저장·응답에는 코드만 사용하며 예외 원문을 포함하지 않는다. */
public enum DrawFailureCode {
	TRANSIENT_STORAGE(true), INTERRUPTED(true), INVALID_EVIDENCE(false), INVALID_RUN(false), NOT_EXECUTABLE(false), INTERNAL_ERROR(false);
	private final boolean retryable;
	/** 실패 종류별 자동 재시도 허용 여부를 정의한다. 실제 예약·횟수 제한은 공통 실행 서비스가 적용한다. */
	DrawFailureCode(boolean retryable) { this.retryable = retryable; }
	/** 이 실패 종류가 제한된 자동 재시도의 대상인지 반환한다. 정합성·미분류 실패는 운영자 확인을 기다린다. */
	public boolean isRetryable() { return retryable; }
}
