package com.getddo.api.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청 빈도를 제한할 Controller 메서드에 붙인다.
 *
 * <p>한도는 사용자별이며 수치는 {@code getddo.rate-limit.policies.<policy>} 설정에서 정한다. 설정에 없는 정책은 제한하지
 * 않는다. 한도를 넘으면 비즈니스 로직과 요청 검증보다 먼저 429로 거절한다.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimited {

	/** 설정에서 한도를 찾을 정책 이름. */
	String policy();

	/**
	 * 같은 사용자의 한도를 더 잘게 나눌 경로 변수 이름. 예를 들어 {@code eventId}를 지정하면 이벤트마다 별도로
	 * {@code scopedLimit}을 적용한다. 비워 두면 사용자 전체 한도만 적용한다.
	 */
	String scopeVariable() default "";
}
