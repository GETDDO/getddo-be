package com.getddo.core.entry.domain;

import java.util.Objects;

import lombok.Getter;

/** 응모 이력 한 건과 화면에 보여 줄 이벤트 제목. 접수 건과 거절 건을 모두 담는다. */
@Getter
public final class EntrySummary {

	private final Entry entry;
	private final String eventTitle;

	public EntrySummary(Entry entry, String eventTitle) {
		this.entry = Objects.requireNonNull(entry, "entry");
		this.eventTitle = Objects.requireNonNull(eventTitle, "eventTitle");
	}
}
