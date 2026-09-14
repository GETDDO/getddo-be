package com.getddo.api.common.exception;

import com.getddo.core.exception.ErrorCode;

/**
 * 요청 해석·검증 실패와 예상하지 못한 서버 오류처럼 특정 도메인에 속하지 않는 API 오류다.
 *
 * <p>업무별 오류는 해당 도메인에서 ErrorCode를 구현해 추가한다.
 * 이 enum에 모든 도메인의 오류를 모으지 않는다.
 * 405·415 등 그 밖의 표준 MVC 오류는 예외 처리기에서 HTTP-{상태값} 코드로 반환한다.</p>
 */
public enum CommonErrorCode implements ErrorCode {

	/** 예상하지 못한 서버 오류. 실제 예외 대신 고정 메시지를 사용자에게 보여준다. */
	INTERNAL_ERROR(500, "COMMON-001", "서버 오류가 발생했습니다."),
	/** DTO 제약 위반, 필수 파라미터·헤더 누락 등 입력 조건을 만족하지 못한 경우. */
	INVALID_INPUT(400, "COMMON-002", "요청 값이 올바르지 않습니다."),
	/** 깨진 JSON이나 숫자·UUID 등으로 변환할 수 없는 요청 값처럼 해석 단계에서 실패한 경우. */
	INVALID_FORMAT(400, "COMMON-005", "요청 형식이 올바르지 않습니다.");

	private final int status;
	private final String code;
	private final String message;

	CommonErrorCode(int status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override
	public int getStatus() {
		return status;
	}

	@Override
	public String getCode() {
		return code;
	}

	@Override
	public String getMessage() {
		return message;
	}
}
