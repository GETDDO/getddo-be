package com.getddo.core.ticket.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.Ticket;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistory;
import com.getddo.core.ticket.domain.UseCommand;
import com.getddo.core.ticket.domain.UseResult;
import com.getddo.core.ticket.domain.UseSelection;
import com.getddo.core.ticket.domain.UsedTicket;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.TicketHistoryRepository;
import com.getddo.core.ticket.repository.TicketRepository;

/**
 * 이벤트 응모가 응모권을 차감할 때 호출하는 단일 진입점.
 *
 * <p>응모권 담당이 응모권의 상태·이력·버전을 소유한다. 호출자는 응모권과 이력을 직접 읽거나 쓰지 않는다.
 * 이 서비스는 차감(USE)만 다루며 반환은 {@link TicketRefundService}가 맡는다.</p>
 *
 * <p>사용자가 보유 조회(T01)의 등급별 보유 장수({@code countByGrade}) 안에서 등급별로 쓸 장수를 고른다. 같은 등급
 * 안에서는 만료가 이른 응모권부터 쓴다. 사용자가 늦게 만료하는 것을 남겨 두는 편이 항상 유리하고 추첨 가중치는
 * 등급으로만 정해지므로, 어느 응모권을 쓰는지는 사용자의 선택에 영향을 주지 않는다. 요청·응답 형태는 공용 스펙
 * {@code 00-requirements/pending-decisions.md}에서 프론트와 확정하기 전이다.</p>
 *
 * <h2>호출 규약</h2>
 * <ol>
 *   <li>응모 행을 먼저 저장한 같은 트랜잭션에서 {@link #use}를 호출한다. 차감과 응모 기록이 함께 커밋되거나
 *       함께 롤백돼야 한다. 사용 이력은 응모 행을 FK로 참조하므로 호출자가 응모를 flush한 뒤 호출한다.</li>
 *   <li>같은 응모 ID로 이미 커밋된 차감이 있으면 추가 차감 없이 같은 결과를 돌려준다. 같은 응모 ID로 차감이
 *       <b>동시에</b> 오는 경우는 이 서비스가 막지 않는다. 호출자가 응모 행 저장(PK)으로 응모 ID마다 처리를
 *       직렬화해야 한다. 응모 행 저장이 실패하면(중복 응모 ID 등) {@code use}를 호출하지 않아야 한다. 같은 ID로
 *       동시에 두 번 호출되면 뒤 요청이 다른 응모권을 차감해 이중 차감이 되고, UNIQUE는 (응모, 응모권) 쌍이라
 *       막지 못한다. 이력을 잠가 다시 확인하는 방식은 빈 결과에서 gap lock을 잡아, 서로 다른 사용자의 동시 차감이
 *       이력 INSERT에서 교착하므로 쓰지 않는다.</li>
 *   <li>등급은 정해진 순서로 정렬해 같은 순서로 잠그고, 등급 안에서는 만료가 이른 순으로 한 장씩 잠근다. 같은
 *       사용자의 동시 차감이 서로 기다릴 뿐 교착하지 않게 하기 위해서다.</li>
 *   <li>이 서비스가 던진 예외는 삼키지 않고 전파한다. 삼키고 진행하면 응모는 있고 차감은 없는 상태가 된다.</li>
 *   <li>트랜잭션 설정은 프록시를 통해서만 적용되므로 다른 Bean에서 호출한다.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class TicketUseService {

	private final TicketRepository ticketRepository;
	private final TicketHistoryRepository historyRepository;
	private final TimeProvider timeProvider;

	/**
	 * 응모 한 건에 대해 사용자가 고른 등급별 장수만큼 응모권을 차감한다.
	 *
	 * <p>호출자 트랜잭션에 참여하며 자체적으로 커밋하지 않는다. 트랜잭션 없이 호출하면 {@code MANDATORY} 설정에 따라
	 * 실패한다. 같은 응모 ID로 다시 호출하면 추가로 차감하지 않고 기존 결과를 {@code replayed=true}로 반환한다.</p>
	 *
	 * @param command 차감 요청
	 * @return 이번에 확정된 차감 결과, 또는 이미 확정된 차감 결과
	 * @throws TicketException 입력이 올바르지 않거나, 고른 등급의 쓸 수 있는 응모권이 고른 장수보다 적거나,
	 *         이미 차감된 응모에 다른 등급·장수·사용자의 요청이 온 경우
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public UseResult use(UseCommand command) {
		validate(command);

		List<TicketHistory> existing = historyRepository.findUseHistories(command.getEventEntryId());
		if (!existing.isEmpty()) {
			return replayOf(command, existing);
		}

		// 후보 응모권을 고르는 기준 시각이다. DATETIME(6)은 마이크로초 아래를 반올림해 저장하므로 미리 잘라, 응답·재조회·
		// 저장 값이 어긋나지 않게 한다.
		Instant candidateAt = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
		List<Ticket> selected = new ArrayList<>();
		for (UseSelection selection : inLockOrder(command.getSelections())) {
			List<Ticket> locked = ticketRepository.findUsableForUpdate(command.getUserId(), selection.getGrade(),
					candidateAt, Math.toIntExact(selection.getCount()));
			if (locked.size() < selection.getCount()) {
				throw new TicketException(TicketErrorCode.TICKET_INSUFFICIENT);
			}
			selected.addAll(locked);
		}

		// 잠금을 기다리는 동안 시간이 흘러 응모권이 만료됐을 수 있다. 잠금을 모두 쥔 지금 처리 시각을 다시 구해 만료된
		// 응모권은 쓰지 않는다. 호출자는 이 시각 이후 커밋까지의 경계를 자신의 응모 마감 판정과 함께 정해야 한다.
		Instant usedAt = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
		if (selected.stream().anyMatch(ticket -> !ticket.getExpiresAt().isAfter(usedAt))) {
			throw new TicketException(TicketErrorCode.TICKET_INSUFFICIENT);
		}
		List<Ticket> used = selected.stream().map(ticket -> ticket.use(usedAt)).toList();
		ticketRepository.updateAll(used);
		historyRepository.saveAll(used.stream()
				.map(ticket -> TicketHistory.use(ticket, command.getEventEntryId(), command.getReason()))
				.toList());
		return new UseResult(used.stream().map(ticket -> new UsedTicket(ticket.getId(), ticket.getGrade())).toList(),
				usedAt, false);
	}

	/** 등급 순으로 정렬한다. 모든 차감이 같은 순서로 잠금을 잡게 한다. */
	private static List<UseSelection> inLockOrder(List<UseSelection> selections) {
		return selections.stream().sorted(Comparator.comparing(UseSelection::getGrade)).toList();
	}

	/**
	 * 이미 차감된 응모의 기존 결과를 돌려준다. 이번 요청이 기존 차감과 같은 사용자·같은 등급별 장수인지 확인하고,
	 * 다르면 같은 응모 ID를 다른 내용으로 다시 쓴 것이므로 거절한다.
	 */
	private UseResult replayOf(UseCommand command, List<TicketHistory> histories) {
		Map<UUID, Ticket> tickets = ticketRepository
				.findAllByIds(histories.stream().map(TicketHistory::getTicketId).toList()).stream()
				.collect(Collectors.toMap(Ticket::getId, Function.identity()));
		boolean sameUser = histories.stream().allMatch(history -> tickets.containsKey(history.getTicketId())
				&& tickets.get(history.getTicketId()).getUserId().equals(command.getUserId()));
		if (!sameUser || !usedCountsByGrade(histories, tickets).equals(requestedCountsByGrade(command))) {
			throw new TicketException(TicketErrorCode.TICKET_USE_MISMATCH);
		}
		return new UseResult(histories.stream()
				.map(history -> new UsedTicket(history.getTicketId(), tickets.get(history.getTicketId()).getGrade()))
				.toList(), histories.get(0).getCreatedAt(), true);
	}

	private static Map<TicketGrade, Long> usedCountsByGrade(List<TicketHistory> histories, Map<UUID, Ticket> tickets) {
		Map<TicketGrade, Long> counts = new EnumMap<>(TicketGrade.class);
		for (TicketHistory history : histories) {
			counts.merge(tickets.get(history.getTicketId()).getGrade(), 1L, Long::sum);
		}
		return counts;
	}

	private static Map<TicketGrade, Long> requestedCountsByGrade(UseCommand command) {
		Map<TicketGrade, Long> counts = new EnumMap<>(TicketGrade.class);
		for (UseSelection selection : command.getSelections()) {
			counts.put(selection.getGrade(), selection.getCount());
		}
		return counts;
	}

	private static void validate(UseCommand command) {
		if (command == null
				|| command.getUserId() == null
				|| command.getEventEntryId() == null
				|| command.getReason() == null
				|| command.getReason().isBlank()
				|| !validSelections(command.getSelections())) {
			throw new TicketException(TicketErrorCode.TICKET_INVALID_USE);
		}
	}

	/** 선택이 하나 이상이고, 장수가 1 이상이며, 같은 등급이 겹치지 않고, 전체 장수가 int 범위 안인지 확인한다. */
	private static boolean validSelections(List<UseSelection> selections) {
		if (selections == null || selections.isEmpty()) {
			return false;
		}
		Set<TicketGrade> grades = EnumSet.noneOf(TicketGrade.class);
		long total = 0;
		for (UseSelection selection : selections) {
			if (selection == null
					|| selection.getCount() < 1
					|| selection.getCount() > Integer.MAX_VALUE
					|| !grades.add(selection.getGrade())) {
				return false;
			}
			total += selection.getCount();
			if (total > Integer.MAX_VALUE) {
				return false;
			}
		}
		return true;
	}
}
