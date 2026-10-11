package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.event.domain.MembershipRule;

/** 시작 알림 등록에 필요한 이벤트 정보만 보관한다. 이벤트를 수정하지 않는다. */
@Getter
public final class EventStartNotification {
	private final UUID id;
	private final String title;
	private final Instant startsAt;
	private final MembershipRule membershipRule;

	/** 이벤트 조회 결과의 필수 값을 검증한다. */
	public EventStartNotification(UUID id, String title, Instant startsAt, MembershipRule membershipRule) {
		this.id = Objects.requireNonNull(id, "id");
		this.title = Objects.requireNonNull(title, "title");
		this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
		this.membershipRule = Objects.requireNonNull(membershipRule, "membershipRule");
	}
}
