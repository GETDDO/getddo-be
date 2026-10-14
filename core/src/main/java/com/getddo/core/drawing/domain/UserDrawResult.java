package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.getddo.core.event.domain.RegisteredEvent;

/** 실제 공개 버전에서 만든 사용자 조회 값이다. 내부 추첨 근거는 포함하지 않는다. */
public final class UserDrawResult {
	private UserDrawResult() { }

	@Getter
	public static final class PublicResults {
		private final UUID eventId;
		private final boolean published;
		private final Instant publishedAt;
		private final Integer revision;
		private final Instant updatedAt;
		private final Instant serverTime;
		private final DrawDisplayStatus displayStatus;
		private final List<PublishedPrize> prizes;

		public PublicResults(UUID eventId, boolean published, Instant publishedAt, Integer revision,
				Instant updatedAt, Instant serverTime, DrawDisplayStatus displayStatus, List<PublishedPrize> prizes) {
			this.eventId = Objects.requireNonNull(eventId, "eventId");
			this.published = published;
			this.publishedAt = publishedAt;
			this.revision = revision;
			this.updatedAt = updatedAt;
			this.serverTime = Objects.requireNonNull(serverTime, "serverTime");
			this.displayStatus = Objects.requireNonNull(displayStatus, "displayStatus");
			this.prizes = List.copyOf(prizes);
		}
	}

	@Getter
	public static final class PublishedPrize {
		private final UUID prizeId;
		private final int rank;
		private final String name;
		private final int winnerCount;
		private final int unfilledCount;
		private final List<MaskedWinner> winners;

		public PublishedPrize(UUID prizeId, int rank, String name, int winnerCount, int unfilledCount,
				List<MaskedWinner> winners) {
			this.prizeId = Objects.requireNonNull(prizeId, "prizeId");
			this.rank = rank;
			this.name = Objects.requireNonNull(name, "name");
			this.winnerCount = winnerCount;
			this.unfilledCount = unfilledCount;
			this.winners = List.copyOf(winners);
		}
	}

	@Getter
	@RequiredArgsConstructor
	public static final class MyResult {
		private final UUID eventId;
		private final MyDrawResultStatus result;
		private final RegisteredEvent.Prize prize;
		private final boolean published;
		private final Integer revision;
		private final Instant serverTime;
	}

	@Getter
	public static final class MaskedWinner {
		private final UUID prizeId;
		private final String maskedName;
		private final String maskedPhoneNum;

		/** 저장소에서 읽은 개인정보를 즉시 가려 원본을 조회 모델에 남기지 않는다. */
		public MaskedWinner(UUID prizeId, String name, String phoneNum) {
			this.prizeId = Objects.requireNonNull(prizeId, "prizeId");
			Objects.requireNonNull(name, "name");
			int length = name.codePointCount(0, name.length());
			if (length <= 1) {
				this.maskedName = name;
			} else {
				String first = name.substring(0, name.offsetByCodePoints(0, 1));
				this.maskedName = length == 2 ? first + "*"
						: first + "*".repeat(length - 2) + name.substring(name.offsetByCodePoints(0, length - 1));
			}
			this.maskedPhoneNum = phoneNum == null ? null : phoneNum.substring(Math.max(0, phoneNum.length() - 4));
		}
	}
}
