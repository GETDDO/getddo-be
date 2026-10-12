package com.getddo.core.drawing.domain;


import lombok.Getter;

/** 저장된 추첨 자료를 표현하는 불변 조회 값이다. */
@Getter
public final class DrawCheckItem {
	private final String code;
	private final boolean passed;
	private final String message;

	public DrawCheckItem(String code, boolean passed, String message) {
		this.code = code;
		this.passed = passed;
		this.message = message;
	}
}
