package com.getddo.core.drawing.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawDisplayStatus;
import com.getddo.core.drawing.domain.UserDrawResult.MaskedWinner;
import com.getddo.core.drawing.domain.UserDrawResult.MyResult;
import com.getddo.core.drawing.domain.UserDrawResult.PublicResults;
import com.getddo.core.drawing.domain.UserDrawResult.PublishedPrize;
import com.getddo.core.drawing.domain.MyDrawResultStatus;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.core.drawing.repository.UserDrawResultRepository;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.RegisteredEvent;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.repository.EventQueryRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

/** 이벤트 상태와 공개 명단을 같은 읽기 스냅샷에서 확인한다. */
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class UserDrawResultService {
	private final EventQueryRepository events;
	private final DrawPublicationRepository publications;
	private final UserDrawResultRepository results;
	private final TimeProvider time;

	/** 실제 공개 기록이 없으면 상태만 제공하고 명단·공개 메타데이터는 숨긴다. */
	public PublicResults publicResults(User user, UUID eventId) {
		requireUser(user, false);
		RegisteredEvent event = event(eventId);
		DrawPublication latest = publications.findLatest(eventId).orElse(null);
		DrawDisplayStatus status = displayStatus(event, latest);
		if (latest == null) {
			return new PublicResults(eventId, false, null, null, null, time.now(), status, List.of());
		}
		List<MaskedWinner> winners = results.findWinners(latest.getId());
		List<PublishedPrize> prizes = event.getPrizes().stream()
				.sorted(Comparator.comparingInt(RegisteredEvent.Prize::getRank).reversed())
				.map(prize -> publishedPrize(prize, winners)).toList();
		DrawPublication first = latest.getRevision() == 1 ? latest
				: publications.findPrevious(eventId, 2).orElseThrow();
		return new PublicResults(eventId, true, first.getPublishedAt(), latest.getRevision(),
				latest.getPublishedAt(), time.now(), status, prizes);
	}

	/** 접수된 본인 응모와 공개 버전의 배정만으로 당첨 여부를 판정한다. */
	public MyResult myResult(User user, UUID eventId) {
		requireUser(user, true);
		RegisteredEvent event = event(eventId);
		DrawPublication latest = publications.findLatest(eventId).orElse(null);
		boolean entered = results.hasEntered(eventId, user.getId());
		MyDrawResultStatus result;
		RegisteredEvent.Prize prize = null;
		if (!entered) {
			result = MyDrawResultStatus.NOT_ENTERED;
		} else if (event.getStatus() == EventStatus.CANCELED) {
			result = MyDrawResultStatus.CANCELED;
		} else if (latest == null) {
			result = MyDrawResultStatus.PENDING;
		} else {
			UUID prizeId = results.findWonPrize(latest.getId(), user.getId()).orElse(null);
			result = prizeId == null ? MyDrawResultStatus.LOST : MyDrawResultStatus.WON;
			if (prizeId != null) {
				prize = event.getPrizes().stream().filter(value -> value.getId().equals(prizeId)).findFirst().orElseThrow();
			}
		}
		return new MyResult(eventId, result, prize, latest != null,
				latest == null ? null : latest.getRevision(), time.now());
	}

	private RegisteredEvent event(UUID id) {
		if (id == null) throw new BusinessException(EventErrorCode.INVALID_QUERY);
		return events.findById(id).orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND)).getDetails();
	}

	private DrawDisplayStatus displayStatus(RegisteredEvent event, DrawPublication publication) {
		if (event.getStatus() == EventStatus.CANCELED) return DrawDisplayStatus.CANCELED;
		if (publication != null) return DrawDisplayStatus.PUBLISHED;
		if (event.getStatus() == EventStatus.NO_ENTRANTS || results.hasNoEntriesRun(event.getId())) {
			return DrawDisplayStatus.NO_ENTRANTS;
		}
		return DrawDisplayStatus.WAITING;
	}

	private PublishedPrize publishedPrize(RegisteredEvent.Prize prize, List<MaskedWinner> winners) {
		List<MaskedWinner> selected = winners.stream().filter(winner -> winner.getPrizeId().equals(prize.getId())).toList();
		return new PublishedPrize(prize.getId(), prize.getRank(), prize.getName(), prize.getWinnerCount(),
				prize.getWinnerCount() - selected.size(), selected);
	}

	private void requireUser(User user, boolean userOnly) {
		if (user == null) throw new BusinessException(EventErrorCode.USER_CONTEXT_REQUIRED);
		if (user.getStatus() != UserStatus.ACTIVE || (userOnly && user.getRole() != UserRole.USER)) {
			throw new BusinessException(EventErrorCode.ACCESS_DENIED);
		}
	}
}
