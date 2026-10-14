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

	/**
	 * EventEntity의 등수 교환 흐름에서만 사용하는 임시 양수 등수다.
	 * 저장소의 중간·최종 flush를 포함한 같은 트랜잭션 안에서 사용한다.
	 *
	 * @throws IllegalArgumentException 임시 등수가 0 이하인 경우
	 */
	void moveToTemporaryRank(int temporaryRank) {
		if (temporaryRank <= 0) {
			throw new IllegalArgumentException("임시 경품 등수는 양수여야 합니다.");
		}
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
