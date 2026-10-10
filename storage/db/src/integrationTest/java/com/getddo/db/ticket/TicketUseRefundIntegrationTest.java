package com.getddo.db.ticket;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import com.getddo.core.ticket.domain.RefundCommand;
import com.getddo.core.ticket.domain.RefundResult;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.domain.UseCommand;
import com.getddo.core.ticket.domain.UseResult;
import com.getddo.core.ticket.domain.UsedTicket;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.TicketRepository;
import com.getddo.core.ticket.service.TicketRefundService;
import com.getddo.core.ticket.service.TicketUseService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 MySQL에서 응모권 차감·반환이 응모권과 이력에 남기는 결과와 그 정합성을 검증한다. */
class TicketUseRefundIntegrationTest extends TicketIntegrationTestSupport {

	private static final String SEPTEMBER_END = "2026-09-30T15:00:00Z";
	private static final String OCTOBER_END = "2026-10-31T15:00:00Z";

	@Autowired
	private TicketUseService useService;
	@Autowired
	private TicketRefundService refundService;
	@Autowired
	private TicketRepository ticketRepository;

	/** 새 미션 청구에 붙은 응모권 한 장을 넣는다. 청구마다 UNIQUE 키가 달라 여러 장을 넣을 수 있다. */
	private UUID ticket(TicketGrade grade, String expiresAt) {
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = seeds.missionClaim(userId, parents, 1);
		return insertTicket(userId, claimId, grade, TicketStatus.AVAILABLE, expiresAt);
	}

	private UseResult use(UUID entryId, long quantity) {
		return transaction.execute(status -> useService.use(new UseCommand(userId, entryId, quantity, "테스트 응모")));
	}

	private RefundResult refund(UUID entryId) {
		return transaction.execute(status -> refundService.refund(new RefundCommand(entryId, "테스트 취소")));
	}

	private String statusOf(UUID ticketId) {
		return jdbc.queryForObject("select status from tickets where id = ?", String.class, bytes(ticketId));
	}

	private long versionOf(UUID ticketId) {
		return count("select version from tickets where id = ?", bytes(ticketId));
	}

	private static List<UUID> ids(List<UsedTicket> tickets) {
		return tickets.stream().map(UsedTicket::getTicketId).toList();
	}

	@Test
	@DisplayName("만료 임박순으로 쓰고 만료 시각이 같으면 낮은 등급을 먼저 쓰며 이미 만료된 응모권은 건너뛴다")
	void usesEarliestExpiryThenLowestGrade() {
		// given
		UUID gold = ticket(TicketGrade.GOLD, SEPTEMBER_END);
		UUID silver = ticket(TicketGrade.SILVER, SEPTEMBER_END);
		UUID later = ticket(TicketGrade.BRONZE, OCTOBER_END);
		UUID expired = ticket(TicketGrade.BRONZE, "2026-09-10T00:00:00Z");
		UUID entry = seeds.eventEntry(userId);
		// when
		UseResult result = use(entry, 2);
		// then
		assertThat(result.isReplayed()).isFalse();
		assertThat(ids(result.getTickets())).containsExactly(silver, gold);
		assertThat(statusOf(silver)).isEqualTo("SPENT");
		assertThat(statusOf(gold)).isEqualTo("SPENT");
		assertThat(statusOf(later)).isEqualTo("AVAILABLE");
		assertThat(statusOf(expired)).isEqualTo("AVAILABLE");
	}

	@Test
	@DisplayName("차감은 응모권을 사용됨·버전 2로 바꾸고 같은 상태와 만료 시각의 사용 이력을 응모에 연결해 남긴다")
	void useWritesTicketAndHistoryConsistently() {
		// given
		UUID ticket = ticket(TicketGrade.SILVER, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		clock.set(Instant.parse("2026-09-15T03:00:00.123456Z"));
		// when
		UseResult result = use(entry, 1);
		// then
		assertThat(result.getUsedAt()).isEqualTo(Instant.parse("2026-09-15T03:00:00.123456Z"));
		assertThat(count("""
				select count(*) from tickets
				where id = ? and status = 'SPENT' and version = 2 and grade = 'SILVER' and expires_at = ?
				""", bytes(ticket), LocalDateTime.ofInstant(Instant.parse(SEPTEMBER_END), ZoneOffset.UTC))).isEqualTo(1);
		assertThat(utc("select updated_at from tickets where id = ?", bytes(ticket))).isEqualTo(result.getUsedAt());
		assertThat(count("""
				select count(*) from ticket_histories
				where ticket_id = ? and operation_type = 'USE' and ticket_version = 2 and status = 'SPENT'
				  and event_entry_id = ? and original_use_history_id is null and expires_at = ?
				""", bytes(ticket), bytes(entry), LocalDateTime.ofInstant(Instant.parse(SEPTEMBER_END), ZoneOffset.UTC)))
				.isEqualTo(1);
		assertThat(utc("select created_at from ticket_histories where ticket_id = ?", bytes(ticket)))
				.isEqualTo(result.getUsedAt());
	}

	@Test
	@DisplayName("응모권이 부족하면 TICKET-006으로 실패하고 어떤 응모권도 바뀌지 않는다")
	void insufficientTicketsChangeNothing() {
		// given
		UUID first = ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		UUID second = ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		// when
		// then
		assertThatThrownBy(() -> use(entry, 3))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INSUFFICIENT));
		assertThat(statusOf(first)).isEqualTo("AVAILABLE");
		assertThat(statusOf(second)).isEqualTo("AVAILABLE");
		assertThat(historyCount(userId)).isZero();
	}

	@Test
	@DisplayName("같은 응모로 다시 차감하면 추가로 차감하지 않고 같은 응모권을 replayed=true로 돌려주며 수량이 다르면 TICKET-008이다")
	void useIsIdempotentPerEntry() {
		// given
		ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		ticket(TicketGrade.BRONZE, OCTOBER_END);
		UUID entry = seeds.eventEntry(userId);
		UseResult first = use(entry, 2);
		// when
		UseResult again = use(entry, 2);
		// then
		assertThat(again.isReplayed()).isTrue();
		assertThat(ids(again.getTickets())).containsExactlyInAnyOrderElementsOf(ids(first.getTickets()));
		assertThat(again.getUsedAt()).isEqualTo(first.getUsedAt());
		assertThat(count("select count(*) from tickets where user_id = ? and status = 'SPENT'", bytes(userId)))
				.isEqualTo(2);
		assertThatThrownBy(() -> use(entry, 1))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_USE_MISMATCH));
	}

	@Test
	@DisplayName("트랜잭션 없이 차감하거나 반환하면 실패한다")
	void requiresCallerTransaction() {
		// given
		UUID entry = seeds.eventEntry(userId);
		// when
		// then
		assertThatThrownBy(() -> useService.use(new UseCommand(userId, entry, 1, "테스트")))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThatThrownBy(() -> refundService.refund(new RefundCommand(entry, "테스트")))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	@DisplayName("반환은 같은 응모권을 반환됨·버전 3으로 되돌리고 등급은 그대로이며 만료는 반환 월의 다다음 달 1일 00:00 KST다")
	void refundReturnsSameTicketsWithNewExpiry() {
		// given
		UUID gold = ticket(TicketGrade.GOLD, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		use(entry, 1);
		clock.set(Instant.parse("2026-09-20T03:00:00Z"));
		// when
		RefundResult result = refund(entry);
		// then
		assertThat(result.isReplayed()).isFalse();
		assertThat(ids(result.getTickets())).containsExactly(gold);
		assertThat(result.getTickets().get(0).getGrade()).isEqualTo(TicketGrade.GOLD);
		assertThat(result.getExpiresAt()).isEqualTo(Instant.parse(OCTOBER_END));
		assertThat(statusOf(gold)).isEqualTo("RETURNED");
		assertThat(versionOf(gold)).isEqualTo(3);
		assertThat(jdbc.queryForObject("select grade from tickets where id = ?", String.class, bytes(gold)))
				.isEqualTo("GOLD");
		assertThat(utc("select expires_at from tickets where id = ?", bytes(gold))).isEqualTo(Instant.parse(OCTOBER_END));
		assertThat(count("""
				select count(*) from ticket_histories r
				join ticket_histories u on u.id = r.original_use_history_id
				where r.ticket_id = ? and r.operation_type = 'REFUND' and r.ticket_version = 3
				  and r.status = 'RETURNED' and r.event_entry_id is null and r.expires_at = ?
				  and u.operation_type = 'USE' and u.event_entry_id = ?
				""", bytes(gold), LocalDateTime.ofInstant(Instant.parse(OCTOBER_END), ZoneOffset.UTC), bytes(entry)))
				.isEqualTo(1);
	}

	@Test
	@DisplayName("반환 만료는 KST 월 경계를 따른다: 9월 30일 23:59:59 KST 반환은 10월 말, 10월 1일 00:00:00 KST 반환은 11월 말")
	void refundExpiryFollowsKstMonthBoundary() {
		// given
		UUID beforeBoundary = ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		UUID entryBefore = seeds.eventEntry(userId);
		clock.set(Instant.parse("2026-09-15T03:00:00Z"));
		use(entryBefore, 1);
		// when
		clock.set(Instant.parse("2026-09-30T14:59:59Z"));
		RefundResult before = refund(entryBefore);
		// then
		assertThat(before.getExpiresAt()).isEqualTo(Instant.parse(OCTOBER_END));
		assertThat(statusOf(beforeBoundary)).isEqualTo("RETURNED");

		// given: 반환된 같은 응모권을 다시 쓴다
		UUID entryAfter = seeds.eventEntry(userId);
		clock.set(Instant.parse("2026-09-30T14:59:59Z"));
		use(entryAfter, 1);
		clock.set(Instant.parse("2026-09-30T15:00:00Z"));
		// when
		RefundResult after = refund(entryAfter);
		// then
		assertThat(after.getExpiresAt()).isEqualTo(Instant.parse("2026-11-30T15:00:00Z"));
		assertThat(ids(after.getTickets())).containsExactly(beforeBoundary);
		assertThat(utc("select expires_at from tickets where id = ?", bytes(beforeBoundary)))
				.isEqualTo(Instant.parse("2026-11-30T15:00:00Z"));
	}

	@Test
	@DisplayName("같은 응모를 다시 반환하면 추가로 반환하지 않고 기존 결과를 replayed=true로 돌려준다")
	void refundIsIdempotent() {
		// given
		ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		ticket(TicketGrade.SILVER, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		use(entry, 2);
		RefundResult first = refund(entry);
		long historiesAfterFirst = historyCount(userId);
		// when
		RefundResult again = refund(entry);
		// then
		assertThat(again.isReplayed()).isTrue();
		assertThat(ids(again.getTickets())).containsExactlyInAnyOrderElementsOf(ids(first.getTickets()));
		assertThat(again.getRefundedAt()).isEqualTo(first.getRefundedAt());
		assertThat(again.getExpiresAt()).isEqualTo(first.getExpiresAt());
		assertThat(historyCount(userId)).isEqualTo(historiesAfterFirst);
		assertThat(count("select count(*) from tickets where user_id = ? and version = 3", bytes(userId)))
				.isEqualTo(2);
	}

	@Test
	@DisplayName("사용 내역이 없는 응모를 반환하면 TICKET-007이다")
	void refundWithoutUseFails() {
		// given
		UUID entry = seeds.eventEntry(userId);
		// when
		// then
		assertThatThrownBy(() -> refund(entry))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_USE_NOT_FOUND));
	}

	@Test
	@DisplayName("사용 뒤 다른 처리가 끼어든 응모권이 있으면 TICKET-009로 실패하고 아무것도 반환하지 않는다")
	void refundRejectsTicketChangedAfterUse() {
		// given
		UUID first = ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		UUID second = ticket(TicketGrade.BRONZE, OCTOBER_END);
		UUID entry = seeds.eventEntry(userId);
		use(entry, 2);
		jdbc.update("update tickets set status = 'EXPIRED', version = 3 where id = ?", bytes(second));
		// when
		// then
		assertThatThrownBy(() -> refund(entry))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_REFUND_STATE_MISMATCH));
		assertThat(statusOf(first)).isEqualTo("SPENT");
		assertThat(count("select count(*) from ticket_histories where operation_type = 'REFUND' "
				+ "and ticket_id in (?, ?)", bytes(first), bytes(second))).isZero();
	}

	@Test
	@DisplayName("반환된 응모권은 새 만료 시각까지 다시 쓸 수 있고 다시 쓰면 버전 4와 새 사용 이력이 남는다")
	void returnedTicketCanBeUsedAgain() {
		// given
		UUID ticket = ticket(TicketGrade.SILVER, SEPTEMBER_END);
		UUID firstEntry = seeds.eventEntry(userId);
		use(firstEntry, 1);
		refund(firstEntry);
		UUID secondEntry = seeds.eventEntry(userId);
		// when
		UseResult result = use(secondEntry, 1);
		// then
		assertThat(ids(result.getTickets())).containsExactly(ticket);
		assertThat(statusOf(ticket)).isEqualTo("SPENT");
		assertThat(versionOf(ticket)).isEqualTo(4);
		assertThat(utc("select expires_at from tickets where id = ?", bytes(ticket))).isEqualTo(Instant.parse(OCTOBER_END));
		assertThat(count("""
				select count(*) from ticket_histories
				where ticket_id = ? and operation_type = 'USE' and event_entry_id = ? and ticket_version = 4
				""", bytes(ticket), bytes(secondEntry))).isEqualTo(1);
	}

	@Test
	@DisplayName("호출자가 같은 트랜잭션에서 응모권을 먼저 읽어 두었어도 잠금 뒤 DB 최신 값(버전·만료)으로 처리한다")
	void usesLatestStateEvenWhenTicketWasLoadedEarlier() {
		// given: 영속성 컨텍스트에는 사용 가능·버전 1·9월 말 만료로 올라가 있다
		UUID ticket = ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		// when: DB에서는 다른 처리가 반환됨·버전 2·10월 말 만료로 바꿔 두었다(여전히 후보에 오른다)
		UseResult result = transaction.execute(status -> {
			ticketRepository.findAllByIds(List.of(ticket));
			jdbc.update("update tickets set status = 'RETURNED', version = 2, expires_at = ? where id = ?",
					LocalDateTime.ofInstant(Instant.parse(OCTOBER_END), ZoneOffset.UTC), bytes(ticket));
			return useService.use(new UseCommand(userId, entry, 1, "테스트 응모"));
		});
		// then: 오래된 값을 쓰면 버전 2와 9월 말 만료가 남는다
		assertThat(ids(result.getTickets())).containsExactly(ticket);
		assertThat(statusOf(ticket)).isEqualTo("SPENT");
		assertThat(versionOf(ticket)).isEqualTo(3);
		assertThat(utc("select expires_at from tickets where id = ?", bytes(ticket)))
				.isEqualTo(Instant.parse(OCTOBER_END));
		assertThat(count("""
				select count(*) from ticket_histories
				where ticket_id = ? and operation_type = 'USE' and ticket_version = 3 and expires_at = ?
				""", bytes(ticket), LocalDateTime.ofInstant(Instant.parse(OCTOBER_END), ZoneOffset.UTC))).isEqualTo(1);
	}
}
