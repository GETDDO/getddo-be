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
import com.getddo.core.drawing.repository.DrawExclusionRepository;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;

import static com.getddo.core.drawing.exception.DrawingErrorCode.*;

@Service
public class DrawSnapshotService {
	public static final String ALGORITHM_VERSION = "weighted-without-replacement-v1";
	private final DrawSnapshotRepository repository;
	private final DrawExclusionRepository exclusions;
	private final TimeProvider time;

	public DrawSnapshotService(DrawSnapshotRepository repository, DrawExclusionRepository exclusions,
			TimeProvider time) {
		this.repository = repository; this.exclusions = exclusions; this.time = time;
	}

	/** 이벤트를 먼저 잠근 뒤 원본 검증·스냅샷·실행별 연결을 하나의 트랜잭션으로 확정한다. */
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
		List<DrawSnapshot.Candidate> all = new ArrayList<>();
		Set<UUID> participantIds = new HashSet<>();
		Set<UUID> users = new HashSet<>();
		Set<UUID> entryIds = new HashSet<>();
		Set<UUID> historyIds = new HashSet<>();
		for (DrawSnapshotSource.Participant participant : participants) {
			if (!participantIds.add(participant.id()) || !users.add(participant.userId())) invalid();
			all.add(validateParticipant(event, participant, now, entryIds, historyIds));
		}
		// 원본 검증은 제외된 응모자도 포함한다. 제외 저장 미연결을 빈 집합으로 처리하지 않는다.
		Set<UUID> excluded = participants.isEmpty() ? Set.of() : exclusions.findExcludedParticipantIds(eventId);
		if (excluded == null || !participantIds.containsAll(excluded)) invalid();
		List<DrawSnapshot.Candidate> candidates = all.stream().filter(candidate -> !excluded.contains(candidate.participantId())).toList();
		DrawRunStatus status = participants.isEmpty() ? DrawRunStatus.NO_ENTRIES
				: candidates.isEmpty() ? DrawRunStatus.NO_CANDIDATES : DrawRunStatus.READY;
		DrawSnapshot.Rules rules = new DrawSnapshot.Rules(1, event.weightingEnabled(), event.type().name(),
				event.membershipRule(), 1, 3, 5, event.prizes());
		return repository.saveInitial(new DrawSnapshot(null, eventId, status, now,
				ALGORITHM_VERSION, rules, candidates));
	}

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

	private static void invalid() { throw new BusinessException(INVALID_EVIDENCE); }
}
