package com.getddo.core.entry.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.user.domain.Membership;

/**
 * 이벤트 응모 요청. 값의 유효성은 {@code EntryService}가 검증한다.
 */
@Getter
public final class EntryCommand {

	private final UUID userId;
	/** 관리자는 응모할 수 없다. */
	private final boolean admin;
	/** 요청 사용자의 멤버십. 관리자는 없을 수 있다. */
	private final Membership membership;
	private final UUID eventId;
	/** 클라이언트가 정한 응모 ID({@code Idempotency-Key}). 같은 ID의 재요청은 새로 응모하지 않는다. */
	private final UUID entryId;
	/** 사용자가 고른 등급별 장수. 없는 등급은 0으로 본다. */
	private final Map<TicketGrade, Long> tickets;

	public EntryCommand(UUID userId, boolean admin, Membership membership, UUID eventId, UUID entryId,
			Map<TicketGrade, Long> tickets) {
		this.userId = userId;
		this.admin = admin;
		this.membership = membership;
		this.eventId = eventId;
		this.entryId = entryId;
		// 호출자가 커맨드를 만든 뒤 맵을 바꿔도 재처리가 같은 요청을 보도록 복사한다. null 키·값은 처리 단계에서 요청 오류로 바꾸므로 허용한다.
		this.tickets = tickets == null ? null : Collections.unmodifiableMap(new HashMap<>(tickets));
	}
}
