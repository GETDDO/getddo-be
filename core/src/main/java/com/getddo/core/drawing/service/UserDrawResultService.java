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

/**
 * 사용자에게 보여 줄 공개 명단과 본인 결과를 조합한다.
 * 이벤트 상태와 공개 버전을 같은 읽기 스냅샷에서 조회해, 조회 도중 공개가 반영되어도
 * 서로 다른 버전의 상태·명단·공개 시각이 한 응답에 섞이지 않게 한다.
 */
@RequiredArgsConstructor
public class UserDrawResultService {
	private final EventQueryRepository events;
	private final DrawPublicationRepository publications;
	private final UserDrawResultRepository results;
	private final TimeProvider time;

	/**
	 * 활성 사용자 또는 관리자에게 실제 공개된 최신 버전의 명단을 제공한다.
	 * 공개 기록이 없으면 추첨 확정 여부와 관계없이 명단과 공개 메타데이터를 숨긴다.
	 * 경품은 화면 발표 순서인 등수 내림차순으로 정렬하며, 최초 공개 시각과 최신 반영 시각을 구분한다.
	 *
	 * @param user 요청 문맥에서 확인한 조회자
	 * @param eventId 조회할 이벤트 식별자
	 * @return 공개 여부·표시 상태와 공개된 경품별 마스킹 명단
	 * @throws BusinessException 사용자 문맥·조회 권한이 없거나 이벤트를 조회할 수 없는 경우
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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

	/**
	 * 활성 일반 사용자의 접수된 응모와 최신 공개 버전으로 본인 결과를 판단한다.
	 * 미응모 여부를 먼저 확인하므로 취소 이벤트에서도 미응모자는 NOT_ENTERED다.
	 * 응모자는 이벤트 취소 시 CANCELED, 공개 전에는 PENDING이며 경품은 공개 후 WON일 때만 제공한다.
	 *
	 * @param user 본인 결과를 조회할 일반 사용자
	 * @param eventId 조회할 이벤트 식별자
	 * @return 본인 결과와 실제 공개 여부·버전, 당첨한 경우의 경품
	 * @throws BusinessException 일반 사용자 권한이 없거나 이벤트를 조회할 수 없는 경우
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
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

	/** 기존 이벤트 조회의 삭제 제외 기준과 오류 형식을 결과 조회에도 적용한다. */
	private RegisteredEvent event(UUID id) {
		if (id == null) throw new BusinessException(EventErrorCode.INVALID_QUERY);
		return events.findById(id).orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_NOT_FOUND)).getDetails();
	}

	/** 취소·실제 공개·응모자 없음 순으로 판단하고, 내부 확정·재추첨 단계는 표시 상태로 노출하지 않는다. */
	private DrawDisplayStatus displayStatus(RegisteredEvent event, DrawPublication publication) {
		if (event.getStatus() == EventStatus.CANCELED) return DrawDisplayStatus.CANCELED;
		if (publication != null) return DrawDisplayStatus.PUBLISHED;
		if (event.getStatus() == EventStatus.NO_ENTRANTS || results.hasNoEntriesRun(event.getId())) {
			return DrawDisplayStatus.NO_ENTRANTS;
		}
		return DrawDisplayStatus.WAITING;
	}

	/** 해당 공개 버전의 당첨자만 경품별로 모아 설정 인원과의 차이를 미충원 수로 제공한다. */
	private PublishedPrize publishedPrize(RegisteredEvent.Prize prize, List<MaskedWinner> winners) {
		List<MaskedWinner> selected = winners.stream().filter(winner -> winner.getPrizeId().equals(prize.getId())).toList();
		return new PublishedPrize(prize.getId(), prize.getRank(), prize.getName(), prize.getWinnerCount(),
				prize.getWinnerCount() - selected.size(), selected);
	}

	/** 공개 명단은 관리자도 조회할 수 있지만 본인 결과는 일반 사용자에게만 허용한다. */
	private void requireUser(User user, boolean userOnly) {
		if (user == null) throw new BusinessException(EventErrorCode.USER_CONTEXT_REQUIRED);
		if (user.getStatus() != UserStatus.ACTIVE || (userOnly && user.getRole() != UserRole.USER)) {
			throw new BusinessException(EventErrorCode.ACCESS_DENIED);
		}
	}
}
