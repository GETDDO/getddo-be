package com.getddo.db.event.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.db.common.entity.BaseUpdatableEntity;

@Entity
@Table(name = "events")
public class EventEntity extends BaseUpdatableEntity {
	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "description", nullable = false, columnDefinition = "text")
	private String description;

	@Column(name = "image_key", length = 500)
	private String imageKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false)
	private EventType eventType;

	@Column(name = "weighting_enabled", nullable = false)
	private boolean weightingEnabled;

	@Column(name = "max_tickets_per_user")
	private Integer maxTicketsPerUser;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private EventStatus status;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "membership_rule", nullable = false)
	private MembershipRule membershipRule;

	@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
	private List<EventPrizeEntity> prizes = new ArrayList<>();

	protected EventEntity() {
	}

	public EventEntity(EventRegistration registration, EventStatus initialStatus) {
		title = registration.getTitle();
		description = registration.getDescription();
		imageKey = registration.getImageKey();
		eventType = registration.getEventType();
		weightingEnabled = registration.isWeightingEnabled();
		maxTicketsPerUser = registration.getMaxTicketsPerUser();
		startsAt = registration.getStartsAt();
		endsAt = registration.getEndsAt();
		status = initialStatus;
		membershipRule = registration.getMembershipRule();
		registration.getPrizes().forEach(prize -> prizes.add(new EventPrizeEntity(this, prize)));
	}

	/**
	 * 제거된 경품을 삭제하고 유지할 경품은 비어 있는 양수 등수로 옮긴다.
	 * 저장소가 중간 flush를 수행한 뒤 최종 등수를 적용해 UNIQUE(event_id, prize_rank) 충돌을 피한다.
	 * 요청의 경품 소유권은 Service에서 검증한다. 여기서는 컬렉션 변경 전에 호출 계약을 확인한다.
	 *
	 * @throws IllegalArgumentException 기존 경품 ID가 이 Entity의 경품 목록에 없는 경우
	 */
	public void preparePrizeReplacement(List<EventRegistration.Prize> replacement) {
		Set<UUID> retainedIds = replacement.stream().map(EventRegistration.Prize::getId)
				.filter(Objects::nonNull).collect(Collectors.toSet());
		Set<UUID> ownedIds = prizes.stream().map(EventPrizeEntity::getId).collect(Collectors.toSet());
		if (!ownedIds.containsAll(retainedIds)) {
			throw new IllegalArgumentException("기존 경품 ID는 해당 이벤트의 경품이어야 합니다.");
		}
		Set<Integer> reservedRanks = new HashSet<>();
		prizes.forEach(prize -> reservedRanks.add(prize.getRank()));
		replacement.forEach(prize -> reservedRanks.add(prize.getRank()));
		prizes.removeIf(prize -> !retainedIds.contains(prize.getId()));
		int temporaryRank = 1;
		for (EventPrizeEntity prize : prizes) {
			while (reservedRanks.contains(temporaryRank)) {
				temporaryRank = Math.incrementExact(temporaryRank);
			}
			prize.moveToTemporaryRank(temporaryRank);
			reservedRanks.add(temporaryRank);
		}
	}

	/** 잠금과 중간 flush 이후 전체 설정을 적용하며 유지하는 경품의 식별자는 바꾸지 않는다. */
	public void modify(EventRegistration settings) {
		title = settings.getTitle();
		description = settings.getDescription();
		imageKey = settings.getImageKey();
		eventType = settings.getEventType();
		weightingEnabled = settings.isWeightingEnabled();
		maxTicketsPerUser = settings.getMaxTicketsPerUser();
		membershipRule = settings.getMembershipRule();
		startsAt = settings.getStartsAt();
		endsAt = settings.getEndsAt();
		Map<UUID, EventPrizeEntity> existing = prizes.stream().collect(Collectors.toMap(EventPrizeEntity::getId, Function.identity()));
		for (EventRegistration.Prize prize : settings.getPrizes()) {
			if (prize.getId() == null) {
				prizes.add(new EventPrizeEntity(this, prize));
			} else {
				existing.get(prize.getId()).modify(prize);
			}
		}
	}

	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public String getImageKey() { return imageKey; }
	public EventType getEventType() { return eventType; }
	public boolean isWeightingEnabled() { return weightingEnabled; }
	public Integer getMaxTicketsPerUser() { return maxTicketsPerUser; }
	public Instant getStartsAt() { return startsAt; }
	public Instant getEndsAt() { return endsAt; }
	public EventStatus getStatus() { return status; }
	/** 호출 서비스의 삭제 조건 검증 후 최초 삭제 시각을 기록한다. 재삭제는 거절하며 이벤트·경품 행은 보존한다. */
	public void markDeleted(Instant deletedAt) {
		if (this.deletedAt != null) {
			throw new IllegalStateException("이미 삭제된 이벤트의 삭제 시각은 변경할 수 없습니다.");
		}
		this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt");
	}

	public Instant getDeletedAt() { return deletedAt; }
	public MembershipRule getMembershipRule() { return membershipRule; }
	public List<EventPrizeEntity> getPrizes() { return List.copyOf(prizes); }
}
