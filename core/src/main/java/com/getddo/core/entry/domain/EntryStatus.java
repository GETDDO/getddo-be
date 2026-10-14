package com.getddo.core.entry.domain;

/** 응모 요청의 처리 결과. */
public enum EntryStatus {
	/** 접수 완료. 응모자와 응모권 차감이 함께 확정됐다. */
	ACCEPTED,
	/** 업무 규칙으로 거절된 시도의 이력. 응모자와 차감이 없다. */
	REJECTED
}
