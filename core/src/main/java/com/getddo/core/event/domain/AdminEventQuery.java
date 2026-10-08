package com.getddo.core.event.domain;

import java.time.LocalDate;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 관리자 목록 검색 입력. fromDate·toDate는 양끝을 포함하는 한국 기준 날짜다. */
@Getter
@RequiredArgsConstructor
public final class AdminEventQuery {
	private final EventStatus status;
	private final EventType eventType;
	private final String keyword;
	private final LocalDate fromDate;
	private final LocalDate toDate;
}
