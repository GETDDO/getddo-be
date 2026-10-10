package com.getddo.core.entry.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.user.domain.Membership;

/**
 * 응모 판단에 필요한 이벤트 정보. 이벤트 등록·수정은 이벤트 도메인이 맡고, 응모는 이 읽기 모델만 본다.
 *
 * <p>이벤트의 상태 전이를 처리하는 코드가 아직 없어 DB의 {@code status}가 시간 흐름과 어긋날 수 있다. 그래서 응모를 받는지는
 * 상태만이 아니라 모집 시간으로도 판정한다.</p>
 */
@Getter
public final class EntryEvent {

	/** 응모가 가중치를 적용하지 않는 이벤트에서 쓸 수 있는 사용 가능 횟수. */
	private static final int UNWEIGHTED_ENTRY_LIMIT = 1;

	private final UUID id;
	private final String title;
	private final EventType eventType;
	private final boolean weightingEnabled;
	/** 사용자별 이벤트 누적 사용 상한. {@code null}이면 상한 없음(월말 소진용). */
	private final Integer maxTicketsPerUser;
	private final MembershipRule membershipRule;
	private final Instant startsAt;
	private final Instant endsAt;
	private final EventStatus status;
	private final boolean deleted;

	public EntryEvent(UUID id, String title, EventType eventType, boolean weightingEnabled, Integer maxTicketsPerUser,
			MembershipRule membershipRule, Instant startsAt, Instant endsAt, EventStatus status, boolean deleted) {
		this.id = Objects.requireNonNull(id, "id");
		this.title = Objects.requireNonNull(title, "title");
		this.eventType = Objects.requireNonNull(eventType, "eventType");
		this.weightingEnabled = weightingEnabled;
		this.maxTicketsPerUser = maxTicketsPerUser;
		this.membershipRule = Objects.requireNonNull(membershipRule, "membershipRule");
		this.startsAt = Objects.requireNonNull(startsAt, "startsAt");
		this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
		this.status = Objects.requireNonNull(status, "status");
		this.deleted = deleted;
	}

	/** 응모권을 쓰는 이벤트인지 알려 준다. */
	public boolean usesTickets() {
		return eventType == EventType.TICKET;
	}

	/** 응모권을 쓰지만 추첨 가중치를 적용하지 않는 이벤트인지 알려 준다. 이 이벤트는 브론즈 응모권 1장만 쓸 수 있다. */
	public boolean isUnweighted() {
		return usesTickets() && !weightingEnabled;
	}

	/**
	 * 사용자가 이 이벤트에서 쓸 수 있는 누적 상한을 돌려준다.
	 *
	 * @return 상한 장수. 응모권을 쓰지 않으면 0, 가중치를 적용하지 않으면 1, 월말 소진용이면 {@code null}
	 */
	public Long ticketLimit() {
		if (!usesTickets()) {
			return 0L;
		}
		if (isUnweighted()) {
			return (long) UNWEIGHTED_ENTRY_LIMIT;
		}
		return maxTicketsPerUser == null ? null : maxTicketsPerUser.longValue();
	}

	/** 삭제되지 않았고, 응모를 받는 상태이며, 모집 시간 안인지 알려 준다. 마감 시각 정각부터는 받지 않는다. */
	public boolean isOpenAt(Instant at) {
		boolean acceptingStatus = status == EventStatus.SCHEDULED || status == EventStatus.OPEN;
		return !deleted && acceptingStatus && !at.isBefore(startsAt) && at.isBefore(endsAt);
	}

	/** 사용자의 멤버십이 이 이벤트의 최소 허용 등급 이상인지 알려 준다. 등급 순서는 우수 &lt; VIP &lt; VVIP다. */
	public boolean allows(Membership membership) {
		return membership != null && membership.ordinal() >= membershipRule.ordinal();
	}
}
