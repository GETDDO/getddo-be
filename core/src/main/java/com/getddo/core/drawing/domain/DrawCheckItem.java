package com.getddo.core.drawing.domain;


import lombok.Getter;

/** 저장 자료 검증의 항목별 결과(코드·통과 여부·메시지)다. */
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
