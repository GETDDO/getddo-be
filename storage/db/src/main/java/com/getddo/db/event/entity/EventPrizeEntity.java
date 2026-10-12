package com.getddo.db.event.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.getddo.core.event.domain.EventRegistration;
import com.getddo.db.common.entity.BaseUpdatableEntity;

@Entity
@Table(name = "event_prizes")
public class EventPrizeEntity extends BaseUpdatableEntity {
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private EventEntity event;

	@Column(name = "prize_rank", nullable = false)
	private int rank;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@Column(name = "image_key", length = 500)
	private String imageKey;

	@Column(name = "winner_count", nullable = false)
	private int winnerCount;

	protected EventPrizeEntity() {
	}

	public EventPrizeEntity(EventEntity event, EventRegistration.Prize prize) {
		this.event = event;
		rank = prize.getRank();
		name = prize.getName();
		description = prize.getDescription();
		imageKey = prize.getImageKey();
		winnerCount = prize.getWinnerCount();
	}

	/** 같은 이벤트 안의 등수 교환을 위한 임시 양수 등수다. 트랜잭션 밖으로 노출하지 않는다. */
	public void moveToTemporaryRank(int temporaryRank) {
		rank = temporaryRank;
	}

	/** 경품 식별자와 생성 시각을 유지하면서 관리자가 전달한 설정을 반영한다. */
	public void modify(EventRegistration.Prize prize) {
		rank = prize.getRank();
		name = prize.getName();
		description = prize.getDescription();
		imageKey = prize.getImageKey();
		winnerCount = prize.getWinnerCount();
	}

	public int getRank() { return rank; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public String getImageKey() { return imageKey; }
	public int getWinnerCount() { return winnerCount; }
}
