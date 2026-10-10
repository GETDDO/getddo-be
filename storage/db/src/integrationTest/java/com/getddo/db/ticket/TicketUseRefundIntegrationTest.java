package com.getddo.db.ticket;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.ticket.domain.RefundCommand;
import com.getddo.core.ticket.domain.RefundResult;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.domain.UseCommand;
import com.getddo.core.ticket.domain.UseResult;
import com.getddo.core.ticket.domain.UseSelection;
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

	/** 같은 등급·만료 시각의 응모권을 여러 장 넣는다. */
	private List<UUID> tickets(int count, TicketGrade grade, String expiresAt) {
		return java.util.stream.IntStream.range(0, count).mapToObj(i -> ticket(grade, expiresAt)).toList();
	}

	/** 등급별로 쓸 장수를 고른 것. */
	private static UseSelection pick(TicketGrade grade, long count) {
		return new UseSelection(grade, count);
	}

	private UseResult use(UUID entryId, UseSelection... selections) {
		return transaction.execute(status -> useService.use(new UseCommand(userId, entryId, List.of(selections), "테스트 응모")));
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

	private long spentCount(TicketGrade grade, String expiresAt) {
		return count("select count(*) from tickets where user_id = ? and grade = ? and expires_at = ? and status = 'SPENT'",
				bytes(userId), grade.name(), LocalDateTime.ofInstant(Instant.parse(expiresAt), ZoneOffset.UTC));
	}

	private static List<UUID> ids(List<UsedTicket> tickets) {
		return tickets.stream().map(UsedTicket::getTicketId).toList();
	}

	private static void assertErrorCode(Throwable thrown, TicketErrorCode expected) {
		assertThat(thrown).isInstanceOfSatisfying(TicketException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(expected));
	}

	@Test
	@DisplayName("고른 등급에서 고른 장수만 차감하고 나머지와 고르지 않은 등급은 건드리지 않는다")
	void usesOnlySelectedCountsFromSelectedGroups() {
		// given: 골드 3장, 실버 5장, 브론즈 1장(다음 달 만료)
		tickets(3, TicketGrade.GOLD, SEPTEMBER_END);
		tickets(5, TicketGrade.SILVER, SEPTEMBER_END);
		UUID otherExpiry = ticket(TicketGrade.BRONZE, OCTOBER_END);
		UUID entry = seeds.eventEntry(userId);
		// when: 골드 3장 중 2장, 실버 5장 중 1장을 고른다
		UseResult result = use(entry, pick(TicketGrade.GOLD, 2), pick(TicketGrade.SILVER, 1));
		// then
		assertThat(result.isReplayed()).isFalse();
		assertThat(result.getQuantity()).isEqualTo(3);
		assertThat(result.countByGrade()).containsEntry(TicketGrade.GOLD, 2L).containsEntry(TicketGrade.SILVER, 1L)
				.containsEntry(TicketGrade.BRONZE, 0L);
		assertThat(spentCount(TicketGrade.GOLD, SEPTEMBER_END)).isEqualTo(2);
		assertThat(spentCount(TicketGrade.SILVER, SEPTEMBER_END)).isEqualTo(1);
		assertThat(statusOf(otherExpiry)).isEqualTo("AVAILABLE");
		assertThat(count("select count(*) from tickets where user_id = ? and status = 'AVAILABLE'", bytes(userId)))
				.isEqualTo(6);
	}

	@Test
	@DisplayName("같은 등급 안에서는 만료가 이른 응모권부터 차감한다")
	void usesEarliestExpiringWithinGrade() {
		// given
		List<UUID> early = tickets(2, TicketGrade.SILVER, SEPTEMBER_END);
		List<UUID> late = tickets(2, TicketGrade.SILVER, OCTOBER_END);
		UUID entry = seeds.eventEntry(userId);
		// when: 실버 4장 중 3장을 고른다
		UseResult result = use(entry, pick(TicketGrade.SILVER, 3));
		// then: 이른 만료 2장을 모두 쓰고 늦은 만료에서 1장만 쓴다
		assertThat(ids(result.getTickets())).containsAll(early);
		assertThat(early).allSatisfy(id -> assertThat(statusOf(id)).isEqualTo("SPENT"));
		assertThat(late.stream().filter(id -> statusOf(id).equals("SPENT")).count()).isEqualTo(1);
	}

	@Test
	@DisplayName("차감은 응모권을 사용됨·버전 2로 바꾸고 같은 상태와 만료 시각의 사용 이력을 응모에 연결해 남긴다")
	void useWritesTicketAndHistoryConsistently() {
		// given
		UUID ticket = ticket(TicketGrade.SILVER, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		clock.set(Instant.parse("2026-09-15T03:00:00.123456Z"));
		// when
		UseResult result = use(entry, pick(TicketGrade.SILVER, 1));
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
	@DisplayName("고른 장수가 등급의 보유 장수보다 크면 TICKET-006으로 실패하고 다른 등급에서 이미 잠근 응모권도 바뀌지 않는다")
	void selectionLargerThanHoldingChangesNothing() {
		// given
		List<UUID> bronze = tickets(2, TicketGrade.BRONZE, SEPTEMBER_END);
		UUID silver = ticket(TicketGrade.SILVER, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		// when: 브론즈 2장은 충분하지만 실버를 2장 골라 부족하다
		// then
		assertThatThrownBy(() -> use(entry, pick(TicketGrade.BRONZE, 2),
				pick(TicketGrade.SILVER, 2)))
				.satisfies(e -> assertErrorCode(e, TicketErrorCode.TICKET_INSUFFICIENT));
		assertThat(bronze).allSatisfy(id -> assertThat(statusOf(id)).isEqualTo("AVAILABLE"));
		assertThat(statusOf(silver)).isEqualTo("AVAILABLE");
		assertThat(historyCount(userId)).isZero();
	}

	@Test
	@DisplayName("보유하지 않은 등급이나 이미 만료된 응모권뿐인 등급을 고르면 TICKET-006이다")
	void unavailableGradeCannotBeSelected() {
		// given
		ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		UUID expired = ticket(TicketGrade.GOLD, "2026-09-10T00:00:00Z");
		UUID entry = seeds.eventEntry(userId);
		// when
		// then
		assertThatThrownBy(() -> use(entry, pick(TicketGrade.SILVER, 1)))
				.satisfies(e -> assertErrorCode(e, TicketErrorCode.TICKET_INSUFFICIENT));
		assertThatThrownBy(() -> use(entry, pick(TicketGrade.GOLD, 1)))
				.satisfies(e -> assertErrorCode(e, TicketErrorCode.TICKET_INSUFFICIENT));
		assertThat(statusOf(expired)).isEqualTo("AVAILABLE");
	}

	@Test
	@DisplayName("같은 응모로 같은 등급·장수를 다시 차감하면 추가로 차감하지 않고 replayed=true이며 다른 내용이면 TICKET-008이다")
	void useIsIdempotentPerEntry() {
		// given
		tickets(3, TicketGrade.BRONZE, SEPTEMBER_END);
		ticket(TicketGrade.SILVER, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		UseResult first = use(entry, pick(TicketGrade.BRONZE, 2));
		// when
		UseResult again = use(entry, pick(TicketGrade.BRONZE, 2));
		// then
		assertThat(again.isReplayed()).isTrue();
		assertThat(ids(again.getTickets())).containsExactlyInAnyOrderElementsOf(ids(first.getTickets()));
		assertThat(again.getUsedAt()).isEqualTo(first.getUsedAt());
		assertThat(count("select count(*) from tickets where user_id = ? and status = 'SPENT'", bytes(userId)))
				.isEqualTo(2);
		assertThatThrownBy(() -> use(entry, pick(TicketGrade.BRONZE, 1)))
				.satisfies(e -> assertErrorCode(e, TicketErrorCode.TICKET_USE_MISMATCH));
		assertThatThrownBy(() -> use(entry, pick(TicketGrade.SILVER, 2)))
				.satisfies(e -> assertErrorCode(e, TicketErrorCode.TICKET_USE_MISMATCH));
	}

	@Test
	@DisplayName("트랜잭션 없이 차감하거나 반환하면 실패한다")
	void requiresCallerTransaction() {
		// given
		UUID entry = seeds.eventEntry(userId);
		UseCommand command = new UseCommand(userId, entry, List.of(pick(TicketGrade.BRONZE, 1)), "테스트");
		// when
		// then
		assertThatThrownBy(() -> useService.use(command))
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
		use(entry, pick(TicketGrade.GOLD, 1));
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
		use(entryBefore, pick(TicketGrade.BRONZE, 1));
		// when
		clock.set(Instant.parse("2026-09-30T14:59:59Z"));
		RefundResult before = refund(entryBefore);
		// then
		assertThat(before.getExpiresAt()).isEqualTo(Instant.parse(OCTOBER_END));
		assertThat(statusOf(beforeBoundary)).isEqualTo("RETURNED");

		// given: 반환된 같은 응모권을 다시 쓴다
		UUID entryAfter = seeds.eventEntry(userId);
		clock.set(Instant.parse("2026-09-30T14:59:59Z"));
		use(entryAfter, pick(TicketGrade.BRONZE, 1));
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
		use(entry, pick(TicketGrade.BRONZE, 1), pick(TicketGrade.SILVER, 1));
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
				.satisfies(e -> assertErrorCode(e, TicketErrorCode.TICKET_USE_NOT_FOUND));
	}

	@Test
	@DisplayName("사용 뒤 다른 처리가 끼어든 응모권이 있으면 TICKET-009로 실패하고 아무것도 반환하지 않는다")
	void refundRejectsTicketChangedAfterUse() {
		// given
		UUID first = ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		UUID second = ticket(TicketGrade.BRONZE, OCTOBER_END);
		UUID entry = seeds.eventEntry(userId);
		use(entry, pick(TicketGrade.BRONZE, 2));
		jdbc.update("update tickets set status = 'EXPIRED', version = 3 where id = ?", bytes(second));
		// when
		// then
		assertThatThrownBy(() -> refund(entry))
				.satisfies(e -> assertErrorCode(e, TicketErrorCode.TICKET_REFUND_STATE_MISMATCH));
		assertThat(statusOf(first)).isEqualTo("SPENT");
		assertThat(count("select count(*) from ticket_histories where operation_type = 'REFUND' "
				+ "and ticket_id in (?, ?)", bytes(first), bytes(second))).isZero();
	}

	@Test
	@DisplayName("반환된 응모권도 같은 등급으로 다시 고를 수 있고 다시 쓰면 버전 4와 새 사용 이력이 남는다")
	void returnedTicketCanBeUsedAgain() {
		// given
		UUID ticket = ticket(TicketGrade.SILVER, SEPTEMBER_END);
		UUID firstEntry = seeds.eventEntry(userId);
		use(firstEntry, pick(TicketGrade.SILVER, 1));
		refund(firstEntry);
		UUID secondEntry = seeds.eventEntry(userId);
		// when
		UseResult result = use(secondEntry, pick(TicketGrade.SILVER, 1));
		// then
		assertThat(ids(result.getTickets())).containsExactly(ticket);
		assertThat(statusOf(ticket)).isEqualTo("SPENT");
		assertThat(versionOf(ticket)).isEqualTo(4);
		assertThat(utc("select expires_at from tickets where id = ?", bytes(ticket))).isEqualTo(Instant.parse(OCTOBER_END));
		assertThat(count("""
				select count(*) from ticket_histories
				where ticket_id = ? and operation_type = 'USE' and event_entry_id = ? and ticket_version = 4
				""", bytes(ticket), bytes(secondEntry))).isEqualTo(1);
		assertThatThrownBy(() -> use(seeds.eventEntry(userId), pick(TicketGrade.SILVER, 1)))
				.satisfies(e -> assertErrorCode(e, TicketErrorCode.TICKET_INSUFFICIENT));
	}

	@Test
	@DisplayName("호출자가 같은 트랜잭션에서 응모권을 먼저 읽어 두었어도 잠금 뒤 DB 최신 값(버전)으로 처리한다")
	void usesLatestStateEvenWhenTicketWasLoadedEarlier() {
		// given: 영속성 컨텍스트에는 사용 가능·버전 1로 올라가 있다
		UUID ticket = ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		// when: DB에서는 다른 처리가 반환됨·버전 2로 바꿔 두었다(여전히 같은 묶음의 후보다)
		UseResult result = transaction.execute(status -> {
			ticketRepository.findAllByIds(List.of(ticket));
			jdbc.update("update tickets set status = 'RETURNED', version = 2 where id = ?", bytes(ticket));
			return useService.use(new UseCommand(userId, entry, List.of(pick(TicketGrade.BRONZE, 1)),
					"테스트 응모"));
		});
		// then: 오래된 값을 쓰면 버전 2가 남는다
		assertThat(ids(result.getTickets())).containsExactly(ticket);
		assertThat(statusOf(ticket)).isEqualTo("SPENT");
		assertThat(versionOf(ticket)).isEqualTo(3);
		assertThat(count("""
				select count(*) from ticket_histories
				where ticket_id = ? and operation_type = 'USE' and ticket_version = 3
				""", bytes(ticket))).isEqualTo(1);
	}

	private <T> T inIsolation(int isolationLevel, Supplier<T> work) {
		TransactionTemplate template = new TransactionTemplate(transaction.getTransactionManager());
		template.setIsolationLevel(isolationLevel);
		return template.execute(status -> work.get());
	}

	/** 호출 트랜잭션과 별개로 커밋한다. 호출 트랜잭션의 스냅샷이 만들어진 뒤에 일어난 지급·반환을 흉내 낸다. */
	private void commitInNewTransaction(Runnable work) {
		TransactionTemplate template = new TransactionTemplate(transaction.getTransactionManager());
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		template.executeWithoutResult(status -> work.run());
	}

	/** 호출 트랜잭션이 첫 일반 조회로 스냅샷을 만든 뒤, 다른 트랜잭션이 같은 등급의 응모권 한 장을 지급해 커밋한다. */
	private UseResult useAfterGrantCommittedFollowingSnapshot(int isolationLevel, UUID entry) {
		return inIsolation(isolationLevel, () -> {
			count("select count(*) from tickets where user_id = ?", bytes(userId));
			commitInNewTransaction(() -> ticket(TicketGrade.BRONZE, SEPTEMBER_END));
			return useService.use(new UseCommand(userId, entry, List.of(pick(TicketGrade.BRONZE, 2)), "테스트 응모"));
		});
	}

	@Test
	@DisplayName("READ COMMITTED 호출자는 자기 스냅샷 뒤에 커밋된 지급분도 보고 차감한다")
	void readCommittedCallerSeesGrantCommittedAfterItsFirstRead() {
		// given: 이미 한 장 있고, 호출자가 읽은 뒤 한 장이 더 지급·커밋된다
		ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		// when
		UseResult result = useAfterGrantCommittedFollowingSnapshot(TransactionDefinition.ISOLATION_READ_COMMITTED, entry);
		// then
		assertThat(result.getQuantity()).isEqualTo(2);
		assertThat(spentCount(TicketGrade.BRONZE, SEPTEMBER_END)).isEqualTo(2);
	}

	@Test
	@DisplayName("REPEATABLE READ 호출자는 자기 스냅샷 뒤에 커밋된 지급분을 보지 못해 보유가 충분해도 TICKET-006이 난다(호출 규약 위반의 증상)")
	void repeatableReadCallerMissesGrantCommittedAfterItsFirstRead() {
		// given
		ticket(TicketGrade.BRONZE, SEPTEMBER_END);
		UUID entry = seeds.eventEntry(userId);
		// when
		// then
		assertThatThrownBy(() -> useAfterGrantCommittedFollowingSnapshot(
				TransactionDefinition.ISOLATION_REPEATABLE_READ, entry))
				.satisfies(e -> assertErrorCode(e, TicketErrorCode.TICKET_INSUFFICIENT));
		assertThat(spentCount(TicketGrade.BRONZE, SEPTEMBER_END)).isZero();
	}
}
