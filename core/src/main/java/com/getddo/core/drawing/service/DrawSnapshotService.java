package com.getddo.core.drawing.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;

/** 정상 접수된 응모의 수량·차감 근거를 검증하고 최초 추첨 입력을 불변 스냅샷으로 확정한다. */
@Service
public class DrawSnapshotService {
	public static final String ALGORITHM_VERSION = "weighted-without-replacement-v1";
	private final DrawSnapshotRepository repository;
	private final TimeProvider time;

	/** 최초 실행 영속성 경계와 서버 기준 시각을 주입한다. */
	public DrawSnapshotService(DrawSnapshotRepository repository, TimeProvider time) {
		this.repository = repository; this.time = time;
	}

	/**
	 * 이벤트를 먼저 잠근 뒤 원본 검증·스냅샷·실행별 연결을 하나의 트랜잭션으로 확정한다.
	 * 정상 응모는 제외 판단 없이 후보로 저장하며, 응모자가 없으면 NO_ENTRIES로 종료한다.
	 * 이미 입력이 확정된 실행은 과거 NO_CANDIDATES를 포함해 원본을 다시 계산하지 않고 반환한다.
	 *
	 * @param eventId 최초 추첨 입력을 준비할 이벤트 식별자
	 * @return 저장된 실행 식별자·후보·조건·확정 시각을 포함한 스냅샷
	 * @throws BusinessException 이벤트가 없거나 실행 시각·상태·응모 근거가 유효하지 않은 경우
	 */
	@Transactional
	public DrawSnapshot prepareInitial(UUID eventId) {
		DrawSnapshotSource.Event event = repository.lockEvent(eventId).orElseThrow(() -> new BusinessException(EVENT_NOT_FOUND));
		Optional<DrawSnapshot> existing = repository.findInitial(eventId);
		if (existing.isPresent()) {
			DrawSnapshot snapshot = existing.get();
			if (Set.of(DrawRunStatus.READY, DrawRunStatus.RUNNING, DrawRunStatus.CONFIRMED,
					DrawRunStatus.NO_ENTRIES, DrawRunStatus.NO_CANDIDATES).contains(snapshot.status())) {
				return snapshot;
			}
			throw new BusinessException(INVALID_RUN);
		}
		Instant now = time.now();
		if (event.deleted() || (event.status() != EventStatus.OPEN && event.status() != EventStatus.CLOSED)
				|| now.isBefore(event.endsAt().plusSeconds(300))) {
			throw new BusinessException(NOT_EXECUTABLE);
		}
		validatePrizes(event);
		List<DrawSnapshotSource.Participant> participants = repository.findParticipants(eventId);
		List<DrawSnapshot.Candidate> candidates = new ArrayList<>();
		Set<UUID> participantIds = new HashSet<>();
		Set<UUID> users = new HashSet<>();
		Set<UUID> entryIds = new HashSet<>();
		Set<UUID> historyIds = new HashSet<>();
		for (DrawSnapshotSource.Participant participant : participants) {
			if (!participantIds.add(participant.id()) || !users.add(participant.userId())) invalid();
			candidates.add(validateParticipant(event, participant, now, entryIds, historyIds));
		}
		DrawRunStatus status = participants.isEmpty() ? DrawRunStatus.NO_ENTRIES : DrawRunStatus.READY;
		DrawSnapshot.Rules rules = new DrawSnapshot.Rules(1, event.weightingEnabled(), event.type().name(),
				event.membershipRule(), 1, 3, 5, event.prizes());
		return repository.saveInitial(new DrawSnapshot(null, eventId, status, now,
				ALGORITHM_VERSION, rules, candidates));
	}

	/** 중복 차감·응모 수량·접수 시각을 검증하고 실제 등급별 차감량으로 후보 가중치를 계산한다. */
	private DrawSnapshot.Candidate validateParticipant(DrawSnapshotSource.Event event,
			DrawSnapshotSource.Participant participant, Instant now, Set<UUID> entryIds, Set<UUID> historyIds) {
		long total = 0, bronze = 0, silver = 0, gold = 0;
		if (participant.entries().isEmpty() || participant.usedTicketCount() < 0) invalid();
		if ((event.type() == EventType.NO_TICKET || !event.weightingEnabled())
				&& participant.entries().size() != 1) invalid();
		Set<UUID> tickets = new HashSet<>();
		try {
			for (DrawSnapshotSource.Entry entry : participant.entries()) {
				if (!entryIds.add(entry.id()) || entry.deductedTicketCount() < 0
						|| entry.uses().size() != entry.deductedTicketCount()
						|| !entry.acceptedAt().isBefore(event.endsAt())) invalid();
				for (DrawSnapshotSource.Use use : entry.uses()) {
					if (!historyIds.add(use.historyId()) || !tickets.add(use.ticketId())
							|| !participant.userId().equals(use.ownerId()) || use.grade() == null) invalid();
					switch (use.grade()) {
						case BRONZE -> bronze = Math.incrementExact(bronze);
						case SILVER -> silver = Math.incrementExact(silver);
						case GOLD -> gold = Math.incrementExact(gold);
					}
				}
				total = Math.addExact(total, entry.deductedTicketCount());
			}
			if (total != participant.usedTicketCount()) invalid();
			if (event.type() == EventType.NO_TICKET && total != 0) invalid();
			if (event.type() == EventType.TICKET && (total == 0 || (!event.weightingEnabled() && total != 1))) invalid();
			long weight = event.type() == EventType.TICKET && event.weightingEnabled()
					? Math.addExact(bronze, Math.addExact(Math.multiplyExact(silver, 3), Math.multiplyExact(gold, 5))) : 1;
			if (weight <= 0) invalid();
			return new DrawSnapshot.Candidate(null, participant.id(), participant.userId(), total, weight,
					new DrawSnapshot.EntryEvidence(1, bronze, silver, gold, participant.entries()),
					new DrawSnapshot.EligibilityEvidence(1, participant.userId(), participant.membership(),
							participant.role(), false, now));
		} catch (ArithmeticException overflow) {
			throw new BusinessException(INVALID_EVIDENCE);
		}
	}

	/** 경품 식별자·등수·당첨 인원과 이벤트 가중치 설정의 정합성을 검증한다. */
	private static void validatePrizes(DrawSnapshotSource.Event event) {
		Set<UUID> ids = new HashSet<>();
		Set<Integer> ranks = new HashSet<>();
		long slots = 0;
		if (event.prizes().isEmpty() || (event.type() == EventType.NO_TICKET && event.weightingEnabled())) invalid();
		for (DrawSnapshotSource.Prize prize : event.prizes()) {
			if (!ids.add(prize.id()) || !ranks.add(prize.rank()) || prize.rank() <= 0 || prize.winnerCount() <= 0) invalid();
			slots += prize.winnerCount();
			if (slots > Integer.MAX_VALUE) invalid();
		}
	}

	/** 잘못된 응모·경품 근거를 일관된 도메인 오류로 거절한다. */
	private static void invalid() { throw new BusinessException(INVALID_EVIDENCE); }
}
