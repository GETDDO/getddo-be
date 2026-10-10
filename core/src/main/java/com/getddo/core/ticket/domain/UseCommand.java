package com.getddo.core.ticket.domain;

import java.util.UUID;

import lombok.Getter;

/**
 * 응모에 응모권을 차감하는 요청. 어떤 응모권을 쓸지는 서버가 정한다.
 *
 * <p>차감 방식을 나중에 등급별 장수 지정으로 넓힐 수 있도록 요청은 이 객체로 묶어 받는다.</p>
 */
@Getter
public final class UseCommand {

	private final UUID userId;
	/** 차감 근거가 되는 저장된 응모 ID. */
	private final UUID eventEntryId;
	/** 차감할 응모권 장수. */
	private final long quantity;
	/** 이력에 남길 사유. */
	private final String reason;

	public UseCommand(UUID userId, UUID eventEntryId, long quantity, String reason) {
		this.userId = userId;
		this.eventEntryId = eventEntryId;
		this.quantity = quantity;
		this.reason = reason;
	}
}
