package com.getddo.core.exception;

/**
 * 서로 다른 업무의 오류를 API 계층에서 같은 방식으로 처리하기 위한 공통 계약이다.
 *
 * <p>각 도메인은 이 인터페이스를 구현한 오류 enum을 정의하고 BusinessException에 전달한다.
 * API 계층은 구체적인 enum 종류를 몰라도 상태·코드·메시지를 읽어 응답할 수 있다.</p>
 *
 * <p>core에 Spring Web 의존성을 추가하지 않기 위해 HTTP 상태를 정수로 표현한다.
 * 다만 HTTP 상태의 의미까지 분리한 설계는 아니며, 현재는 오류와 응답 상태를 함께 관리한다.</p>
 */
public interface ErrorCode {

	/** 오류 응답의 HTTP 상태. 업무 오류에 맞는 4xx 또는 서버 오류에 맞는 5xx를 지정한다. */
	int getStatus();

	/** 클라이언트가 오류를 구분하는 식별자. 메시지 문구를 수정하더라도 같은 오류의 코드는 유지한다. */
	String getCode();

	/** 응답에 그대로 노출되는 메시지. SQL, 접속 정보, 민감한 입력값 등 내부 상세를 포함하지 않는다. */
	String getMessage();
}
