package com.getddo.core.ticket.domain;

import java.util.UUID;

import lombok.Getter;

/** 응모에 쓴 응모권을 모두 되돌리는 요청. */
@Getter
public final class RefundCommand {

	/** 반환할 사용 이력의 응모 ID. */
	private final UUID eventEntryId;
	/** 이력에 남길 사유. */
	private final String reason;

	public RefundCommand(UUID eventEntryId, String reason) {
		this.eventEntryId = eventEntryId;
		this.reason = reason;
	}
}
