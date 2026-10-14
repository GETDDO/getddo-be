package com.getddo.db.entry;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.entry.domain.Entry;
import com.getddo.core.entry.domain.EntryHistoryQuery;
import com.getddo.core.entry.domain.EntryStatus;
import com.getddo.core.entry.domain.EntrySummary;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.service.EntryHistoryService;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.db.ticket.TicketGrantSeeds;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 MySQL에서 내 응모 이력(E05·E06)이 접수 건과 거절 건을 최신순으로 함께 보여 주고 조건을 지키는지 검증한다. */
class EntryHistoryIntegrationTest extends EntryIntegrationTestSupport {

	private static final PageQuery FIRST_PAGE = new PageQuery(1, 20);

	@Autowired
	private EntryHistoryService historyService;

	private UUID eventA;
	private UUID eventB;
	private UUID acceptedA;
	private UUID rejectedA;
	private UUID acceptedB;

	/** 시간 순서대로 이벤트 A 접수, 이벤트 A 거절(상한 초과), 이벤트 B 접수가 남는다. */
	private void seedHistory() {
		giveTickets(userId, TicketGrade.GOLD, 6);
		eventA = weightedEvent(5);
		eventB = weightedEvent(5);
		acceptedA = UUID.randomUUID();
		rejectedA = UUID.randomUUID();
		acceptedB = UUID.randomUUID();
		entryService.enter(command(userId, eventA, acceptedA, tickets(TicketGrade.GOLD, 1)));
		clock.set(NOW.plusSeconds(10));
		assertThatThrownBy(() -> entryService.enter(command(userId, eventA, rejectedA, tickets(TicketGrade.GOLD, 5))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		clock.set(NOW.plusSeconds(20));
		entryService.enter(command(userId, eventB, acceptedB, tickets(TicketGrade.GOLD, 1)));
	}

	private static List<UUID> ids(PageResult<EntrySummary> page) {
		return page.getItems().stream().map(summary -> summary.getEntry().getId()).toList();
	}

	@Test
	@DisplayName("전체 조회는 접수와 거절을 기록 시각 내림차순으로 함께 돌려주고 항목에 요청·차감·거절 정보가 담긴다")
	void listsAcceptedAndRejectedNewestFirst() {
		// given
		seedHistory();
		// when
		PageResult<EntrySummary> page = historyService.findMyEntries(userId,
				new EntryHistoryQuery(null, null, null, null), FIRST_PAGE);
		// then
		assertThat(ids(page)).containsExactly(acceptedB, rejectedA, acceptedA);
		assertThat(page.getTotalElements()).isEqualTo(3);
		EntrySummary rejected = page.getItems().get(1);
		assertThat(rejected.getEventTitle()).isEqualTo("응모 테스트 이벤트");
		Entry rejection = rejected.getEntry();
		assertThat(rejection.getStatus()).isEqualTo(EntryStatus.REJECTED);
		assertThat(rejection.getRejectionCode()).isEqualTo("ENTRY-007");
		assertThat(rejection.getRequestedTicketsByGrade().get(TicketGrade.GOLD)).isEqualTo(5L);
		assertThat(rejection.getDeductedTicketCount()).isZero();
		assertThat(rejection.getParticipantId()).isNull();
		Entry accepted = page.getItems().get(0).getEntry();
		assertThat(accepted.getStatus()).isEqualTo(EntryStatus.ACCEPTED);
		assertThat(accepted.getRequestedTicketsByGrade().get(TicketGrade.GOLD)).isEqualTo(1L);
		assertThat(accepted.getDeductedTicketsByGrade().get(TicketGrade.GOLD)).isEqualTo(1L);
		assertThat(accepted.getRejectionCode()).isNull();
	}

	@Test
	@DisplayName("상태·이벤트·기간 조건은 해당 이력만 돌려주고 기간은 시작 포함, 끝 제외다")
	void filtersByStatusEventAndPeriod() {
		// given
		seedHistory();
		// when
		PageResult<EntrySummary> rejectedOnly = historyService.findMyEntries(userId,
				new EntryHistoryQuery(null, EntryStatus.REJECTED, null, null), FIRST_PAGE);
		PageResult<EntrySummary> acceptedOnly = historyService.findMyEntries(userId,
				new EntryHistoryQuery(null, EntryStatus.ACCEPTED, null, null), FIRST_PAGE);
		PageResult<EntrySummary> ofEventA = historyService.findMyEntries(userId,
				new EntryHistoryQuery(eventA, null, null, null), FIRST_PAGE);
		PageResult<EntrySummary> period = historyService.findMyEntries(userId,
				new EntryHistoryQuery(null, null, NOW.plusSeconds(10), NOW.plusSeconds(20)), FIRST_PAGE);
		// then
		assertThat(ids(rejectedOnly)).containsExactly(rejectedA);
		assertThat(ids(acceptedOnly)).containsExactly(acceptedB, acceptedA);
		assertThat(ids(ofEventA)).containsExactly(rejectedA, acceptedA);
		assertThat(ids(period)).containsExactly(rejectedA);
		assertThat(period.getTotalElements()).isEqualTo(1);
	}

	@Test
	@DisplayName("이벤트별 조회는 그 이벤트의 접수와 거절만 보여 주고 다른 사용자의 이력은 보이지 않는다")
	void eventHistoryShowsOnlyMyEntriesOfThatEvent() {
		// given
		seedHistory();
		UUID other = seeds.user();
		giveTickets(other, TicketGrade.GOLD, 6);
		UUID otherRejected = UUID.randomUUID();
		assertThatThrownBy(() -> entryService.enter(command(other, eventA, otherRejected,
				tickets(TicketGrade.GOLD, 6)))).satisfies(e -> assertEntryError(e, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		// when
		PageResult<EntrySummary> mine = historyService.findMyEntriesOfEvent(userId, eventA, FIRST_PAGE);
		PageResult<EntrySummary> others = historyService.findMyEntriesOfEvent(other, eventA, FIRST_PAGE);
		PageResult<EntrySummary> nobody = historyService.findMyEntriesOfEvent(seeds.user(), eventA, FIRST_PAGE);
		// then
		assertThat(ids(mine)).containsExactly(rejectedA, acceptedA);
		assertThat(ids(others)).containsExactly(otherRejected);
		assertThat(nobody.getItems()).isEmpty();
		assertThat(nobody.getTotalElements()).isZero();
	}

	@Test
	@DisplayName("기록 시각이 같으면 ID 내림차순으로 DB 정렬과 같게 보이고 페이지가 중복·누락 없이 이어진다")
	void sameTimestampOrdersByIdAndPagesWithoutGaps() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 6);
		UUID event = weightedEvent(5);
		for (int i = 0; i < 4; i++) {
			entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.GOLD, 1)));
		}
		List<UUID> expected = jdbc.query("select id from event_entries where user_id = ? order by created_at desc, id desc",
				(row, index) -> TicketGrantSeeds.uuid(row.getBytes("id")), bytes(userId));
		// when
		List<UUID> paged = new java.util.ArrayList<>();
		for (int page = 1; page <= 4; page++) {
			paged.addAll(ids(historyService.findMyEntries(userId, new EntryHistoryQuery(null, null, null, null),
					new PageQuery(page, 1))));
		}
		// then
		assertThat(paged).containsExactlyElementsOf(expected);
		assertThat(paged).doesNotHaveDuplicates().hasSize(4);
	}

	@Test
	@DisplayName("페이지 오프셋이 int 범위를 넘어도 예외 없이 빈 목록과 전체 건수를 돌려준다")
	void hugePageReturnsEmptyItemsWithTotal() {
		// given
		seedHistory();
		// when
		PageResult<EntrySummary> page = historyService.findMyEntries(userId,
				new EntryHistoryQuery(null, null, null, null), new PageQuery(Integer.MAX_VALUE, 100));
		PageResult<EntrySummary> beyondTotal = historyService.findMyEntries(userId,
				new EntryHistoryQuery(null, null, null, null), new PageQuery(2, 20));
		// then
		assertThat(page.getItems()).isEmpty();
		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(beyondTotal.getItems()).isEmpty();
		assertThat(beyondTotal.getTotalElements()).isEqualTo(3);
	}

	@Test
	@DisplayName("없는 이벤트의 이벤트별 조회는 ENTRY-004이고, 삭제된 이벤트의 이력은 볼 수 있다")
	void missingEventIsNotFoundButDeletedEventKeepsHistory() {
		// given
		UUID deleted = seeds.event("TICKET", true, 5, "excellent", NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
				"OPEN", null);
		giveTickets(userId, TicketGrade.GOLD, 1);
		UUID entry = UUID.randomUUID();
		entryService.enter(command(userId, deleted, entry, tickets(TicketGrade.GOLD, 1)));
		jdbc.update("update events set deleted_at = ? where id = ?",
				java.time.LocalDateTime.ofInstant(NOW, java.time.ZoneOffset.UTC), bytes(deleted));
		// when
		// then
		assertThatThrownBy(() -> historyService.findMyEntriesOfEvent(userId, UUID.randomUUID(), FIRST_PAGE))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_FOUND));
		assertThat(ids(historyService.findMyEntriesOfEvent(userId, deleted, FIRST_PAGE))).containsExactly(entry);
	}
}
