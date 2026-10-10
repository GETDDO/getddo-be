package com.getddo.core.ticket.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import lombok.Getter;

/**
 * 응모에 응모권을 차감하는 요청. 사용자가 등급별로 쓸 장수를 고른다.
 *
 * <p>등급마다 쓸 장수는 그 등급의 현재 사용 가능한 장수를 넘을 수 없다. 값의 유효성은
 * {@code TicketUseService.use}가 검증한다.</p>
 */
@Getter
public final class UseCommand {

	private final UUID userId;
	/** 차감 근거가 되는 저장된 응모 ID. */
	private final UUID eventEntryId;
	/** 사용자가 고른 등급과 장수. 같은 등급은 한 번만 나와야 한다. 호출자가 원본 목록을 바꿔도 영향이 없도록 복사해 둔다. */
	private final List<UseSelection> selections;
	/** 이력에 남길 사유. */
	private final String reason;

	public UseCommand(UUID userId, UUID eventEntryId, List<UseSelection> selections, String reason) {
		this.userId = userId;
		this.eventEntryId = eventEntryId;
		this.selections = selections == null ? null : Collections.unmodifiableList(new ArrayList<>(selections));
		this.reason = reason;
	}
}
