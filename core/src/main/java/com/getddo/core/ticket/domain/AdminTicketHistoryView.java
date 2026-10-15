package com.getddo.core.ticket.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 관리자에게 보여 주는 응모권 처리 이력 한 건(AO05). 사용자 이력에 사용자 이름과 이벤트·미션 제목을 더한다. */
@Getter
public final class AdminTicketHistoryView {

	private final TicketHistoryView history;
	private final UUID userId;
	private final String userName;
	/** 이벤트와 관련 없는 처리면 null. */
	private final String eventTitle;
	/** 미션 지급으로 만들어진 응모권이 아니면 null. */
	private final String missionTitle;

	public AdminTicketHistoryView(TicketHistoryView history, UUID userId, String userName, String eventTitle,
			String missionTitle) {
		this.history = Objects.requireNonNull(history, "history");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.userName = Objects.requireNonNull(userName, "userName");
		this.eventTitle = eventTitle;
		this.missionTitle = missionTitle;
	}
}
