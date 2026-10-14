package com.getddo.db.entry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.getddo.core.entry.domain.EntryCommand;
import com.getddo.core.entry.domain.EntryReceipt;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.user.domain.Membership;
import com.getddo.db.ticket.ConcurrentTasks;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 MySQL에서 업무 거절이 응모 트랜잭션 롤백과 별개로 이력에 남고, 같은 응모 ID의 재요청이 처음 거절을 돌려주는지 검증한다. */
class EntryRejectionIntegrationTest extends EntryIntegrationTestSupport {

	/** 거절 건의 저장 행을 읽는다. */
	private Map<String, Object> row(UUID entry) {
		return jdbc.queryForMap("""
				select status, rejection_code, rejection_reason, participant_id, event_id, user_id,
				       requested_ticket_count, deducted_ticket_count, requested_bronze_count,
				       requested_silver_count, requested_gold_count, created_at
				from event_entries where id = ?
				""", bytes(entry));
	}

	private long rows(UUID entry) {
		return count("select count(*) from event_entries where id = ?", bytes(entry));
	}

	@Test
	@DisplayName("누적 상한 초과는 응모자·차감 없이 REJECTED 이력으로 남고 요청한 등급별 장수를 보존한다")
	void limitExceededIsStoredAsRejection() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 6);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.GOLD, 6))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		Map<String, Object> stored = row(entry);
		assertThat(stored.get("status")).isEqualTo("REJECTED");
		assertThat(stored.get("rejection_code")).isEqualTo("ENTRY-007");
		assertThat(stored.get("rejection_reason")).isEqualTo(EntryErrorCode.TICKET_LIMIT_EXCEEDED.getMessage());
		assertThat(stored.get("participant_id")).isNull();
		assertThat(stored.get("requested_ticket_count")).isEqualTo(6);
		assertThat(stored.get("requested_gold_count")).isEqualTo(6);
		assertThat(stored.get("requested_silver_count")).isEqualTo(0);
		assertThat(stored.get("deducted_ticket_count")).isEqualTo(0);
		assertThat(com.getddo.db.ticket.TicketGrantSeeds.uuid((byte[]) stored.get("event_id"))).isEqualTo(event);
		assertThat(com.getddo.db.ticket.TicketGrantSeeds.uuid((byte[]) stored.get("user_id"))).isEqualTo(userId);
		assertThat(count("select count(*) from event_participants where user_id = ?", bytes(userId))).isZero();
		assertThat(spentTickets(userId)).isZero();
	}

	@Test
	@DisplayName("응모권 부족(TICKET-006)도 등급별 요청 장수와 함께 거절 이력으로 남는다")
	void ticketInsufficientIsStoredWithPerGradeRequest() {
		// given
		giveTickets(userId, TicketGrade.SILVER, 1);
		giveTickets(userId, TicketGrade.BRONZE, 2);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry,
				tickets(TicketGrade.BRONZE, 2, TicketGrade.SILVER, 2))))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INSUFFICIENT));
		Map<String, Object> stored = row(entry);
		assertThat(stored.get("status")).isEqualTo("REJECTED");
		assertThat(stored.get("rejection_code")).isEqualTo("TICKET-006");
		assertThat(stored.get("requested_bronze_count")).isEqualTo(2);
		assertThat(stored.get("requested_silver_count")).isEqualTo(2);
		assertThat(stored.get("requested_gold_count")).isEqualTo(0);
		assertThat(stored.get("requested_ticket_count")).isEqualTo(4);
		assertThat(spentTickets(userId)).isZero();
	}

	@Test
	@DisplayName("관리자·멤버십 미달·기간 밖 거절도 각각 ENTRY-002·003·005로 남는다")
	void adminMembershipAndClosedEventRejectionsAreStored() {
		// given
		UUID open = weightedEvent(5);
		UUID vvipOnly = seeds.event("TICKET", true, 5, "vvip", NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
				"OPEN", null);
		UUID closed = seeds.event("TICKET", true, 5, "excellent", NOW.minusSeconds(172_800),
				NOW.minusSeconds(86_400), "OPEN", null);
		UUID adminEntry = UUID.randomUUID();
		UUID membershipEntry = UUID.randomUUID();
		UUID closedEntry = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(new EntryCommand(userId, true, null, open, adminEntry,
				tickets(TicketGrade.BRONZE, 1)))).satisfies(e -> assertEntryError(e, EntryErrorCode.ADMIN_ENTRY_FORBIDDEN));
		assertThatThrownBy(() -> entryService.enter(new EntryCommand(userId, false, Membership.EXCELLENT, vvipOnly,
				membershipEntry, tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.ENTRY_MEMBERSHIP_NOT_MET));
		assertThatThrownBy(() -> entryService.enter(command(userId, closed, closedEntry, tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_OPEN));
		assertThat(row(adminEntry).get("rejection_code")).isEqualTo("ENTRY-002");
		assertThat(row(membershipEntry).get("rejection_code")).isEqualTo("ENTRY-003");
		assertThat(row(closedEntry).get("rejection_code")).isEqualTo("ENTRY-005");
	}

	@Test
	@DisplayName("이미 응모한 이벤트에 다른 키로 다시 응모하면 ENTRY-006 거절이 남고 기존 접수 응모는 그대로다")
	void alreadyEnteredIsStoredWithoutTouchingAcceptedEntry() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 2);
		UUID event = unweightedEvent();
		UUID accepted = UUID.randomUUID();
		UUID again = UUID.randomUUID();
		entryService.enter(command(userId, event, accepted, tickets(TicketGrade.BRONZE, 1)));
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, again, tickets(TicketGrade.BRONZE, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.ALREADY_ENTERED));
		assertThat(row(again).get("rejection_code")).isEqualTo("ENTRY-006");
		assertThat(row(accepted).get("status")).isEqualTo("ACCEPTED");
		assertThat(spentTickets(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("거절된 응모 ID를 같은 요청으로 다시 보내면 상황이 바뀌어도 처음 거절을 돌려주고 새로 남기거나 차감하지 않는다")
	void sameKeyReplayReturnsOriginalRejection() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 1);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		EntryCommand request = command(userId, event, entry, tickets(TicketGrade.BRONZE, 2));
		assertThatThrownBy(() -> entryService.enter(request))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INSUFFICIENT));
		Object createdAt = row(entry).get("created_at");
		giveTickets(userId, TicketGrade.BRONZE, 5);
		clock.set(NOW.plusSeconds(60));
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(request))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INSUFFICIENT));
		assertThat(rows(entry)).isEqualTo(1);
		assertThat(row(entry).get("created_at")).isEqualTo(createdAt);
		assertThat(spentTickets(userId)).isZero();
	}

	@Test
	@DisplayName("마감 뒤에도 거절된 응모 ID의 재요청은 처음 거절을 돌려준다")
	void rejectedReplayAfterClosingKeepsOriginalError() {
		// given
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		EntryCommand request = new EntryCommand(userId, true, null, event, entry, tickets(TicketGrade.BRONZE, 1));
		assertThatThrownBy(() -> entryService.enter(request))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.ADMIN_ENTRY_FORBIDDEN));
		clock.set(NOW.plusSeconds(10 * 86_400L));
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(request))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.ADMIN_ENTRY_FORBIDDEN));
		assertThat(rows(entry)).isEqualTo(1);
	}

	@Test
	@DisplayName("거절된 응모 ID로 다른 구성·다른 이벤트·다른 사용자의 요청이 오면 ENTRY-009이고 이력은 그대로다")
	void sameKeyWithDifferentRequestConflicts() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 1);
		UUID event = weightedEvent(5);
		UUID otherEvent = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 2))))
				.isInstanceOf(TicketException.class);
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.SILVER, 2))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.IDEMPOTENCY_CONFLICT));
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.BRONZE, 3))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.IDEMPOTENCY_CONFLICT));
		assertThatThrownBy(() -> entryService.enter(command(userId, otherEvent, entry, tickets(TicketGrade.BRONZE, 2))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.IDEMPOTENCY_CONFLICT));
		assertThatThrownBy(() -> entryService.enter(command(seeds.user(), event, entry, tickets(TicketGrade.BRONZE, 2))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.IDEMPOTENCY_CONFLICT));
		assertThat(rows(entry)).isEqualTo(1);
		assertThat(row(entry).get("rejection_code")).isEqualTo("TICKET-006");
	}

	@Test
	@DisplayName("요청 형식 오류·없는 이벤트·삭제된 이벤트·같은 키의 다른 요청은 이력으로 남기지 않는다")
	void nonRecordableFailuresLeaveNoHistory() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 2);
		UUID event = weightedEvent(5);
		UUID deleted = seeds.event("TICKET", true, 5, "excellent", NOW.minusSeconds(86_400), NOW.plusSeconds(86_400),
				"OPEN", NOW.minusSeconds(60));
		UUID invalid = UUID.randomUUID();
		UUID missing = UUID.randomUUID();
		UUID deletedEntry = UUID.randomUUID();
		UUID accepted = UUID.randomUUID();
		UUID conflicting = accepted;
		entryService.enter(command(userId, event, accepted, tickets(TicketGrade.GOLD, 1)));
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, invalid, tickets(TicketGrade.GOLD, -1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.INVALID_ENTRY_REQUEST));
		assertThatThrownBy(() -> entryService.enter(command(userId, UUID.randomUUID(), missing,
				tickets(TicketGrade.GOLD, 1)))).satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_FOUND));
		assertThatThrownBy(() -> entryService.enter(command(userId, deleted, deletedEntry, tickets(TicketGrade.GOLD, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_FOUND));
		assertThatThrownBy(() -> entryService.enter(command(userId, event, conflicting, tickets(TicketGrade.GOLD, 2))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.IDEMPOTENCY_CONFLICT));
		// 장수 합계가 int를 넘는 요청은 관리자 거절이 먼저 일어나도 형식 오류라서 남기지 않는다.
		UUID huge = UUID.randomUUID();
		assertThatThrownBy(() -> entryService.enter(new EntryCommand(userId, true, null, event, huge,
				tickets(TicketGrade.BRONZE, Integer.MAX_VALUE, TicketGrade.SILVER, 1))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.ADMIN_ENTRY_FORBIDDEN));
		assertThat(rows(invalid) + rows(missing) + rows(deletedEntry) + rows(huge)).isZero();
		assertThat(count("select count(*) from event_entries where user_id = ?", bytes(userId))).isEqualTo(1);
		assertThat(row(accepted).get("status")).isEqualTo("ACCEPTED");
	}

	@Test
	@DisplayName("거절 뒤 다른 키로 응모하면 접수되고 이력에는 거절과 접수가 따로 남는다")
	void acceptedEntryAfterRejectionKeepsBoth() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 2);
		UUID event = weightedEvent(5);
		UUID rejected = UUID.randomUUID();
		UUID accepted = UUID.randomUUID();
		assertThatThrownBy(() -> entryService.enter(command(userId, event, rejected, tickets(TicketGrade.GOLD, 6))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		// when
		EntryReceipt receipt = entryService.enter(command(userId, event, accepted, tickets(TicketGrade.GOLD, 2)));
		// then
		assertThat(receipt.isCreated()).isTrue();
		assertThat(row(rejected).get("status")).isEqualTo("REJECTED");
		assertThat(row(accepted).get("status")).isEqualTo("ACCEPTED");
		assertThat(usedTicketCount(userId, event)).isEqualTo(2);
	}

	@RepeatedTest(5)
	@DisplayName("같은 응모 ID의 거절 요청이 동시에 와도 이력은 한 건이고 모두 같은 거절을 받는다")
	void concurrentSameKeyRejectionsLeaveOneRow() throws Exception {
		// given
		giveTickets(userId, TicketGrade.GOLD, 6);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		int callers = 6;
		ExecutorService executor = Executors.newFixedThreadPool(callers);
		CountDownLatch start = new CountDownLatch(1);
		List<CompletableFuture<Throwable>> results = new ArrayList<>();
		try {
			for (int i = 0; i < callers; i++) {
				results.add(CompletableFuture.supplyAsync(() -> {
					try {
						start.await(10, TimeUnit.SECONDS);
						entryService.enter(command(userId, event, entry, tickets(TicketGrade.GOLD, 6)));
						return null;
					} catch (Throwable failure) {
						return failure;
					}
				}, executor));
			}
			// when
			start.countDown();
			List<Throwable> failures = new ArrayList<>();
			for (CompletableFuture<Throwable> result : results) {
				failures.add(result.get(30, TimeUnit.SECONDS));
			}
			// then
			assertThat(failures).hasSize(callers).allSatisfy(failure ->
					assertEntryError(failure, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
			assertThat(rows(entry)).isEqualTo(1);
			assertThat(spentTickets(userId)).isZero();
		} finally {
			start.countDown();
			ConcurrentTasks.shutdownAndAwait(executor);
		}
	}

	@Test
	@DisplayName("거절 이력의 시각은 거절한 시점의 시계 값이다")
	void rejectionTimeIsTheClockAtRejection() {
		// given
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		Instant at = NOW.plusSeconds(120);
		clock.set(at);
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry, tickets(TicketGrade.GOLD, 6))))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		assertThat(((java.time.LocalDateTime) row(entry).get("created_at")).toInstant(java.time.ZoneOffset.UTC))
				.isEqualTo(at);
	}
}
