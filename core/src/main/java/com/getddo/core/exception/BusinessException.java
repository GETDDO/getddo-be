package com.getddo.core.exception;

import java.util.Objects;

/**
 * 예상 가능한 업무 규칙 위반을 오류 코드와 함께 전달하는 예외다.
 *
 * <p>Service 등에서 {@code throw new BusinessException(errorCode)}로 사용한다.
 * core에서는 HTTP 응답을 만들지 않고, Controller 경계까지 전달된 예외를
 * api의 GlobalExceptionHandler가 공통 응답으로 변환한다.</p>
 *
 * <p>RuntimeException을 상속하므로 Spring 트랜잭션 경계 밖으로 전파되면 기본 롤백 대상이다.
 * 예외를 중간에서 잡아 처리하거나 별도 롤백 규칙을 지정하면 동작이 달라질 수 있다.</p>
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	/**
	 * 응답 변환에 필요한 오류 계약을 보관한다.
	 *
	 * @param errorCode 도메인 또는 공통 오류 코드
	 * @throws NullPointerException 오류 코드가 null인 경우. 코드 없는 업무 예외 생성을 허용하지 않는다.
	 */
	public BusinessException(ErrorCode errorCode) {
		super(Objects.requireNonNull(errorCode, "errorCode").getMessage());
		this.errorCode = errorCode;
	}

	/** 예외 처리기가 HTTP 상태와 응답 본문을 결정할 때 사용하는 오류 계약을 반환한다. */
	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
