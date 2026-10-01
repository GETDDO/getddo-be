package com.getddo.api.common.context;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.swagger.v3.oas.annotations.Parameter;

/** 시연용 헤더를 DB와 대조한 User를 Controller 파라미터로 받는다. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Parameter(hidden = true)
public @interface CurrentUser {
	/**
	 * 기본값은 USER의 헤더·DB 멤버십을 필수로 확인하고 ADMIN은 헤더 형식만 확인한다.
	 * false이면 두 역할 모두 생략할 수 있으며, 전달한 멤버십은 DB 값과 대조한다.
	 */
	boolean membershipRequired() default true;
}
