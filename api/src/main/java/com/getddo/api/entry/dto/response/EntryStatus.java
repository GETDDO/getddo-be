package com.getddo.api.entry.dto.response;

/** 응모 영수증의 처리 결과. 거절된 시도는 저장하지 않으므로 현재 응답은 {@code ACCEPTED}만 쓴다. */
public enum EntryStatus {
	ACCEPTED,
	REJECTED
}
