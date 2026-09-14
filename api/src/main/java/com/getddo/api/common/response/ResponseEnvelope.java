package com.getddo.api.common.response;

import com.getddo.core.exception.ErrorCode;

/**
 * JSON API의 성공·실패 응답을 동일한 최상위 구조로 감싼다.
 *
 * <p>Controller는 {@link #success(Object)}로 응답 DTO를 감싸고,
 * 예외 처리기는 {@link #fail(ErrorCode)}로 실패 응답을 만든다.
 * 이 객체는 응답 본문만 표현하므로 HTTP 상태는 Controller나 예외 처리기가 별도로 지정한다.
 * 실패를 HTTP 200으로 반환하고 success 값만 false로 바꾸는 용도로 사용하지 않는다.</p>
 *
 * @param success 요청 처리 성공 여부
 * @param code 클라이언트가 분기 처리에 사용할 식별 코드. 성공 시 SUCCESS
 * @param message 사용자에게 공개 가능한 안내 메시지
 * @param data 성공 응답의 실제 데이터. 데이터가 없거나 실패한 경우 null
 * @param <T> Controller가 반환하는 응답 DTO의 타입
 */
public record ResponseEnvelope<T>(boolean success, String code, String message, T data) {

	/**
	 * 응답 DTO를 성공 응답으로 감싼다.
	 *
	 * <p>예: {@code ResponseEnvelope.success(responseDto)}.
	 * 반환할 데이터가 없으면 {@code ResponseEnvelope.success(null)}을 사용한다.
	 * 이는 본문이 있는 JSON 응답이므로, 본문을 보내지 않는 HTTP 204 응답에는 사용하지 않는다.</p>
	 */
	public static <T> ResponseEnvelope<T> success(T data) {
		return new ResponseEnvelope<>(true, "SUCCESS", "성공했습니다.", data);
	}

	/**
	 * 오류 계약의 코드와 공개 메시지만 응답에 넣는다.
	 * 예외 원문이나 스택 트레이스는 포함하지 않으며, HTTP 상태는 errorCode.getStatus()로 별도 설정한다.
	 */
	public static ResponseEnvelope<Void> fail(ErrorCode errorCode) {
		return new ResponseEnvelope<>(false, errorCode.getCode(), errorCode.getMessage(), null);
	}
}
