package com.getddo.core.entry.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.entry.domain.Entry;
import com.getddo.core.entry.domain.EntryCommand;
import com.getddo.core.entry.domain.EntryEvent;
import com.getddo.core.entry.domain.EntryParticipant;
import com.getddo.core.entry.domain.EntryReceipt;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.entry.repository.EntryEventReader;
import com.getddo.core.entry.repository.EntryParticipantRepository;
import com.getddo.core.entry.repository.EntryRepository;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.UseCommand;
import com.getddo.core.ticket.domain.UseResult;
import com.getddo.core.ticket.domain.UseSelection;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.service.TicketUseService;

/**
 * 이벤트 응모 한 건을 한 트랜잭션에서 처리한다. {@link EntryService}만 호출한다.
 *
 * <p>응모 기록·응모권 차감·차감 이력·응모자 누적 수량은 이 트랜잭션에서 함께 확정되거나 함께 취소된다. 트랜잭션은
 * {@code READ COMMITTED}다. 응모권 차감({@link TicketUseService})이 후보 응모권을 잠그지 않고 읽으므로, 격리 수준이 더 높으면
 * 트랜잭션 시작 뒤에 지급·반환된 응모권을 보지 못해 보유가 충분해도 {@code TICKET-006}이 날 수 있다.</p>
 *
 * <h2>처리 순서와 잠금 순서</h2>
 * <ol>
 *   <li>관리자 거절, 같은 응모 ID가 이미 접수됐는지 확인(있으면 기존 결과, 마감 뒤에도 같다).</li>
 *   <li>이벤트 행을 공유 잠금으로 잠그고 멤버십·기간·요청 형태·1회/누적 상한을 검증한다.</li>
 *   <li>응모자를 PK로 잠가(처음이면 새로 저장) 같은 사용자의 동시 응모를 직렬화한다.</li>
 *   <li>응모 행을 저장·반영한 뒤 응모권을 차감한다. 응모 행 저장이 실패하면 차감을 호출하지 않는다.</li>
 * </ol>
 * <p>잠금 순서는 이벤트(공유) → 응모자 → 응모권으로 고정해 교착을 피한다.</p>
 */
@Service
@RequiredArgsConstructor
public class EntryRecorder {

	private static final String ENTRY_REASON = "이벤트 응모";

	private final EntryEventReader eventReader;
	private final EntryParticipantRepository participantRepository;
	private final EntryRepository entryRepository;
	private final TicketUseService ticketUseService;
	private final TimeProvider timeProvider;

	/**
	 * 응모를 접수한다.
	 *
	 * <p>같은 응모 ID로 이미 접수된 응모가 있으면 새로 처리하지 않고 기존 영수증을 {@code created=false}로 돌려준다. 같은 첫
	 * 응모가 동시에 오면 늦은 쪽은 응모자나 응모 저장의 UNIQUE 위반으로 실패하고, 재처리는 호출자({@link EntryService})가
	 * 새 트랜잭션에서 한다.</p>
	 *
	 * @throws EntryException 관리자 응모, 멤버십 미달, 이벤트 없음·미개방, 요청 형식 오류, 1회·누적 상한 위반,
	 *         브론즈 부족, 같은 응모 ID의 다른 요청
	 * @throws TicketException 등급별 사용 가능한 응모권이 부족한 경우({@code TICKET-006})
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public EntryReceipt record(EntryCommand command) {
		Map<TicketGrade, Long> tickets = normalize(command.getTickets());
		if (command.isAdmin()) {
			throw new EntryException(EntryErrorCode.ADMIN_ENTRY_FORBIDDEN);
		}
		Optional<Entry> existing = entryRepository.findById(command.getEntryId());
		if (existing.isPresent()) {
			return replay(command, tickets, existing.get());
		}

		EntryEvent event = eventReader.findForShare(command.getEventId())
				.filter(found -> !found.isDeleted())
				.orElseThrow(() -> new EntryException(EntryErrorCode.EVENT_NOT_FOUND));
		if (!event.allows(command.getMembership())) {
			throw new EntryException(EntryErrorCode.ENTRY_MEMBERSHIP_NOT_MET);
		}
		// DATETIME(6)은 마이크로초 아래를 반올림해 저장하므로 미리 잘라 응답·재조회·저장 값이 어긋나지 않게 한다.
		Instant acceptedAt = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
		if (!event.isOpenAt(acceptedAt)) {
			throw new EntryException(EntryErrorCode.EVENT_NOT_OPEN);
		}
		long total = requireValidRequest(event, tickets);

		Optional<EntryParticipant> found = participantRepository.findForUpdate(event.getId(), command.getUserId());
		// 응모자 행을 기다리는 동안 같은 응모 ID가 먼저 접수됐을 수 있다. 그 응모가 사용량을 이미 채웠다면 이어지는 상한 검사가
		// 같은 요청을 상한 초과로 거절하므로, 잠금을 쥔 지금 최신 상태로 다시 확인해 기존 결과를 돌려준다.
		if (found.isPresent()) {
			Optional<Entry> acceptedMeanwhile = entryRepository.findById(command.getEntryId());
			if (acceptedMeanwhile.isPresent()) {
				return replay(command, tickets, acceptedMeanwhile.get());
			}
		}
		requireWithinLimit(event, found, total);
		EntryParticipant participant = found.orElseGet(() -> participantRepository
				.create(EntryParticipant.first(event.getId(), command.getUserId(), acceptedAt)));

		entryRepository.save(Entry.accepted(command.getEntryId(), participant, command.getUserId(),
				Math.toIntExact(total), acceptedAt));
		if (total > 0) {
			useTickets(event, command, tickets);
			participantRepository.addUsedTicketCount(participant.getId(), total);
		}
		// 접수 완료는 도착이 아니라 확정 시점 기준이다. 차감을 마치고도 마감 전이어야 한다.
		if (!event.isOpenAt(timeProvider.now())) {
			throw new EntryException(EntryErrorCode.EVENT_NOT_OPEN);
		}
		return new EntryReceipt(command.getEntryId(), event.getId(), event.getTitle(), tickets, acceptedAt, true);
	}

	/** 같은 응모 ID로 이미 접수된 응모의 기존 결과를 돌려준다. 다른 사용자·이벤트·응모권 구성이면 거절한다. */
	private EntryReceipt replay(EntryCommand command, Map<TicketGrade, Long> tickets, Entry entry) {
		long total = tickets.values().stream().mapToLong(Long::longValue).sum();
		if (!entry.getUserId().equals(command.getUserId()) || !entry.getEventId().equals(command.getEventId())
				|| entry.getRequestedTicketCount() != total) {
			throw new EntryException(EntryErrorCode.IDEMPOTENCY_CONFLICT);
		}
		EntryEvent event = eventReader.find(entry.getEventId())
				.orElseThrow(() -> new EntryException(EntryErrorCode.EVENT_NOT_FOUND));
		Map<TicketGrade, Long> deducted = tickets;
		if (total > 0) {
			try {
				// 차감 서비스가 기존 사용 이력과 요청이 같은지 대조한다. 같으면 추가 차감 없이 기존 결과를 돌려준다.
				UseResult replayed = ticketUseService.use(useCommand(command, tickets));
				deducted = replayed.countByGrade();
			} catch (TicketException mismatch) {
				if (mismatch.getErrorCode() == TicketErrorCode.TICKET_USE_MISMATCH) {
					throw new EntryException(EntryErrorCode.IDEMPOTENCY_CONFLICT);
				}
				throw mismatch;
			}
		}
		return new EntryReceipt(entry.getId(), event.getId(), event.getTitle(), deducted, entry.getCreatedAt(), false);
	}

	private void useTickets(EntryEvent event, EntryCommand command, Map<TicketGrade, Long> tickets) {
		try {
			ticketUseService.use(useCommand(command, tickets));
		} catch (TicketException insufficient) {
			// 가중치를 적용하지 않는 이벤트는 브론즈만 쓸 수 있으므로 일반 보유 부족과 구분해 안내한다.
			if (event.isUnweighted() && insufficient.getErrorCode() == TicketErrorCode.TICKET_INSUFFICIENT) {
				throw new EntryException(EntryErrorCode.BRONZE_REQUIRED);
			}
			throw insufficient;
		}
	}

	private static UseCommand useCommand(EntryCommand command, Map<TicketGrade, Long> tickets) {
		List<UseSelection> selections = tickets.entrySet().stream()
				.map(selected -> new UseSelection(selected.getKey(), selected.getValue()))
				.toList();
		return new UseCommand(command.getUserId(), command.getEntryId(), selections, ENTRY_REASON);
	}

	/** 요청의 등급별 장수에서 0을 지우고 음수를 거절한다. 요청이 없으면 빈 맵이다. */
	static Map<TicketGrade, Long> normalize(Map<TicketGrade, Long> requested) {
		Map<TicketGrade, Long> tickets = new EnumMap<>(TicketGrade.class);
		if (requested == null) {
			return tickets;
		}
		for (Map.Entry<TicketGrade, Long> selected : requested.entrySet()) {
			Long count = selected.getValue();
			if (selected.getKey() == null || count == null || count < 0) {
				throw new EntryException(EntryErrorCode.INVALID_ENTRY_REQUEST);
			}
			if (count > 0) {
				tickets.put(selected.getKey(), count);
			}
		}
		return tickets;
	}

	/** 이벤트 유형이 허용하는 요청인지 확인하고 요청 장수 합계를 돌려준다. */
	private static long requireValidRequest(EntryEvent event, Map<TicketGrade, Long> tickets) {
		long total = tickets.values().stream().mapToLong(Long::longValue).sum();
		boolean valid;
		if (!event.usesTickets()) {
			valid = total == 0;
		} else if (event.isUnweighted()) {
			valid = tickets.equals(Map.of(TicketGrade.BRONZE, 1L));
		} else {
			valid = total >= 1;
		}
		if (!valid || total > Integer.MAX_VALUE) {
			throw new EntryException(EntryErrorCode.INVALID_ENTRY_REQUEST);
		}
		return total;
	}

	/** 한 번만 응모할 수 있는 이벤트의 중복 응모와 누적 사용 상한 초과를 거절한다. */
	private static void requireWithinLimit(EntryEvent event, Optional<EntryParticipant> participant, long total) {
		if (!event.usesTickets() || event.isUnweighted()) {
			if (participant.isPresent()) {
				throw new EntryException(EntryErrorCode.ALREADY_ENTERED);
			}
			return;
		}
		Long limit = event.ticketLimit();
		long used = participant.map(EntryParticipant::getUsedTicketCount).orElse(0L);
		if (limit != null && used + total > limit) {
			throw new EntryException(EntryErrorCode.TICKET_LIMIT_EXCEEDED);
		}
	}
}
