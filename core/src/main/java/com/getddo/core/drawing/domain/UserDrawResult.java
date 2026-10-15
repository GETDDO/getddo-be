package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

import com.getddo.core.event.domain.RegisteredEvent;

/** 실제 공개 버전에서 만든 사용자 조회 값이다. 내부 추첨 근거는 포함하지 않는다. */
public final class UserDrawResult {
	private UserDrawResult() { }

	/** 실제 공개 여부와 사용자 표시 상태, 공개된 경품별 명단을 한 응답으로 묶는다. */
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

		/**
		 * 미공개 명단이 응답에 섞이지 않도록 공개 상태·메타데이터·명단의 조합을 검증한다.
		 * 취소된 이벤트에도 기존 공개 이력이 있을 수 있어 취소 상태와 공개 여부를 따로 유지한다.
		 *
		 * @param publishedAt 최초 공개 시각, 공개 전에는 null
		 * @param revision 최신 공개 버전, 공개 전에는 null
		 * @param updatedAt 최신 공개 버전 반영 시각, 공개 전에는 null
		 * @param prizes 공개 전에는 빈 목록이며 생성 후 외부에서 변경할 수 없도록 복사하는 경품 목록
		 */
		public PublicResults(UUID eventId, boolean published, Instant publishedAt, Integer revision,
				Instant updatedAt, Instant serverTime, DrawDisplayStatus displayStatus, List<PublishedPrize> prizes) {
			List<PublishedPrize> copied = List.copyOf(prizes);
			if (!published && (publishedAt != null || revision != null || updatedAt != null || !copied.isEmpty())) {
				throw new IllegalArgumentException("unpublished results cannot include publication data");
			}
			if (published && (publishedAt == null || revision == null || revision < 1 || updatedAt == null)) {
				throw new IllegalArgumentException("published results require publication metadata");
			}
			if ((published && displayStatus != DrawDisplayStatus.PUBLISHED && displayStatus != DrawDisplayStatus.CANCELED)
					|| (!published && displayStatus == DrawDisplayStatus.PUBLISHED)) {
				throw new IllegalArgumentException("display status does not match publication state");
			}
			this.eventId = Objects.requireNonNull(eventId, "eventId");
			this.published = published;
			this.publishedAt = publishedAt;
			this.revision = revision;
			this.updatedAt = updatedAt;
			this.serverTime = Objects.requireNonNull(serverTime, "serverTime");
			this.displayStatus = Objects.requireNonNull(displayStatus, "displayStatus");
			this.prizes = copied;
		}
	}

	/** 경품 하나의 설정 인원과 해당 공개 버전의 실제 당첨자·미충원 수를 표현한다. */
	@Getter
	public static final class PublishedPrize {
		private final UUID prizeId;
		private final int rank;
		private final String name;
		private final int winnerCount;
		private final int unfilledCount;
		private final List<MaskedWinner> winners;

		/**
		 * 당첨자 수와 미충원 수의 합이 설정 인원과 일치하고 모든 당첨자가 이 경품에 속하는지 검증한다.
		 * 명단을 복사해 생성 후 당첨자 목록만 바뀌면서 인원 정보가 어긋나는 것을 막는다.
		 */
		public PublishedPrize(UUID prizeId, int rank, String name, int winnerCount, int unfilledCount,
				List<MaskedWinner> winners) {
			List<MaskedWinner> copied = List.copyOf(winners);
			if (rank < 1 || winnerCount < 1 || unfilledCount < 0 || unfilledCount > winnerCount
					|| copied.size() != winnerCount - unfilledCount) {
				throw new IllegalArgumentException("invalid published prize counts");
			}
			this.prizeId = Objects.requireNonNull(prizeId, "prizeId");
			if (copied.stream().anyMatch(winner -> !prizeId.equals(winner.getPrizeId()))) {
				throw new IllegalArgumentException("winner belongs to another prize");
			}
			this.rank = rank;
			this.name = Objects.requireNonNull(name, "name");
			this.winnerCount = winnerCount;
			this.unfilledCount = unfilledCount;
			this.winners = copied;
		}
	}

	/** 본인 결과와 실제 공개 버전을 함께 보관하며, 당첨한 경우에만 경품을 가진다. */
	@Getter
	public static final class MyResult {
		private final UUID eventId;
		private final MyDrawResultStatus result;
		private final RegisteredEvent.Prize prize;
		private final boolean published;
		private final Integer revision;
		private final Instant serverTime;

		/**
		 * 본인 결과와 공개 버전·경품의 조합이 응답 계약과 어긋나지 않도록 검증한다.
		 * WON·LOST는 공개된 경우에만, PENDING은 공개 전인 경우에만 허용한다.
		 * NOT_ENTERED·CANCELED는 과거 공개 이력이 있을 수 있으므로 공개 여부를 별도로 보관한다.
		 */
		public MyResult(UUID eventId, MyDrawResultStatus result, RegisteredEvent.Prize prize,
				boolean published, Integer revision, Instant serverTime) {
			this.eventId = Objects.requireNonNull(eventId, "eventId");
			this.result = Objects.requireNonNull(result, "result");
			if ((result == MyDrawResultStatus.WON) != (prize != null)) {
				throw new IllegalArgumentException("only WON results require a prize");
			}
			if (published ? revision == null || revision < 1 : revision != null) {
				throw new IllegalArgumentException("publication revision does not match published flag");
			}
			if (((result == MyDrawResultStatus.WON || result == MyDrawResultStatus.LOST) && !published)
					|| (result == MyDrawResultStatus.PENDING && published)) {
				throw new IllegalArgumentException("result does not match publication state");
			}
			this.prize = prize;
			this.published = published;
			this.revision = revision;
			this.serverTime = Objects.requireNonNull(serverTime, "serverTime");
		}
	}

	/** 경품별 명단 조합에 필요한 연결 정보와 마스킹된 개인정보만 보관한다. */
	@Getter
	public static final class MaskedWinner {
		private final UUID prizeId;
		private final String maskedName;
		private final String maskedPhoneNum;

		/**
		 * 저장소에서 읽은 개인정보를 즉시 가려 원본 이름·전화번호를 조회 모델에 남기지 않는다.
		 * 두 글자 이름은 첫 글자만, 세 글자 이상은 처음과 끝만 남기고 한 글자 이름은 전부 가린다.
		 * 글자 수는 유니코드 코드 포인트로 계산해 한 글자를 이루는 서로게이트 쌍이 잘리지 않게 한다.
		 * 전화번호는 끝 네 자리까지 남기며, 빈 이름과 null 전화번호에 별도 대체 문구를 넣지 않는다.
		 */
		public MaskedWinner(UUID prizeId, String name, String phoneNum) {
			this.prizeId = Objects.requireNonNull(prizeId, "prizeId");
			Objects.requireNonNull(name, "name");
			int length = name.codePointCount(0, name.length());
			if (length == 0) {
				this.maskedName = name;
			} else if (length == 1) {
				this.maskedName = "*";
			} else {
				String first = name.substring(0, name.offsetByCodePoints(0, 1));
				this.maskedName = length == 2 ? first + "*"
						: first + "*".repeat(length - 2) + name.substring(name.offsetByCodePoints(0, length - 1));
			}
			this.maskedPhoneNum = phoneNum == null ? null : phoneNum.substring(Math.max(0, phoneNum.length() - 4));
		}
	}
}
