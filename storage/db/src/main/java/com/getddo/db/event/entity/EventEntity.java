package com.getddo.db.event.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

	@OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
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

	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public String getImageKey() { return imageKey; }
	public EventType getEventType() { return eventType; }
	public boolean isWeightingEnabled() { return weightingEnabled; }
	public Integer getMaxTicketsPerUser() { return maxTicketsPerUser; }
	public Instant getStartsAt() { return startsAt; }
	public Instant getEndsAt() { return endsAt; }
	public EventStatus getStatus() { return status; }
	public Instant getDeletedAt() { return deletedAt; }
	public MembershipRule getMembershipRule() { return membershipRule; }
	public List<EventPrizeEntity> getPrizes() { return List.copyOf(prizes); }
}
