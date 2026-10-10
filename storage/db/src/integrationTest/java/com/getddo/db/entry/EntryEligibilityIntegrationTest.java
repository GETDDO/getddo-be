package com.getddo.db.entry;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.entry.domain.EntryEligibility;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.user.domain.Membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 MySQL에서 응모 자격 사전 조회(E07)의 가능 여부·사유·사용량·잔여량을 검증한다. */
class EntryEligibilityIntegrationTest extends EntryIntegrationTestSupport {

	private EntryEligibility check(UUID user, UUID event) {
		return eligibilityService.check(user, false, Membership.EXCELLENT, event);
	}

	@Test
	@DisplayName("가중치 이벤트에서 응모할 수 있으면 사유가 없고 사용량 0, 잔여 상한 5, 보유 장수 합계를 돌려준다")
	void eligibleForWeightedEvent() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 2);
		giveTickets(userId, TicketGrade.BRONZE, 3);
		UUID event = weightedEvent(5);
		// when
		EntryEligibility result = check(userId, event);
		// then
		assertThat(result.isCanEnter()).isTrue();
		assertThat(result.getReasons()).isEmpty();
		assertThat(result.getUsedTicketCount()).isZero();
		assertThat(result.getRemainingTicketLimit()).isEqualTo(5L);
		assertThat(result.getAvailableTicketBalance()).isEqualTo(5);
		assertThat(result.getServerTime()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("응모한 뒤에는 사용량이 늘고 잔여 상한이 줄며 상한을 다 쓰면 TICKET_LIMIT_EXCEEDED 사유가 붙는다")
	void usageAndRemainingLimitFollowEntries() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 8);
		UUID event = weightedEvent(5);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 3)));
		// when
		EntryEligibility afterThree = check(userId, event);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 2)));
		EntryEligibility afterFive = check(userId, event);
		// then
		assertThat(afterThree.isCanEnter()).isTrue();
		assertThat(afterThree.getUsedTicketCount()).isEqualTo(3);
		assertThat(afterThree.getRemainingTicketLimit()).isEqualTo(2L);
		assertThat(afterThree.getAvailableTicketBalance()).isEqualTo(5);
		assertThat(afterFive.isCanEnter()).isFalse();
		assertThat(afterFive.getReasons()).containsExactly(EntryErrorCode.TICKET_LIMIT_EXCEEDED.name());
		assertThat(afterFive.getRemainingTicketLimit()).isZero();
	}

	@Test
	@DisplayName("월말 소진용 이벤트는 잔여 상한이 null이고, 보유가 없으면 INSUFFICIENT_TICKETS 사유다")
	void monthEndEventHasNullRemainingLimit() {
		// given
		UUID event = weightedEvent(null);
		// when
		EntryEligibility withoutTickets = check(userId, event);
		giveTickets(userId, TicketGrade.SILVER, 2);
		EntryEligibility withTickets = check(userId, event);
		// then
		assertThat(withoutTickets.getRemainingTicketLimit()).isNull();
		assertThat(withoutTickets.getReasons()).containsExactly("INSUFFICIENT_TICKETS");
		assertThat(withTickets.isCanEnter()).isTrue();
		assertThat(withTickets.getRemainingTicketLimit()).isNull();
		assertThat(withTickets.getAvailableTicketBalance()).isEqualTo(2);
	}

	@Test
	@DisplayName("가중치 미적용 이벤트는 쓸 수 있는 장수가 브론즈 장수이고 브론즈가 없으면 BRONZE_REQUIRED, 응모한 뒤에는 ALREADY_ENTERED다")
	void unweightedEventCountsBronzeOnly() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 3);
		UUID event = unweightedEvent();
		// when
		EntryEligibility onlyGold = check(userId, event);
		giveTickets(userId, TicketGrade.BRONZE, 2);
		EntryEligibility withBronze = check(userId, event);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets(TicketGrade.BRONZE, 1)));
		EntryEligibility entered = check(userId, event);
		// then
		assertThat(onlyGold.isCanEnter()).isFalse();
		assertThat(onlyGold.getAvailableTicketBalance()).isZero();
		assertThat(onlyGold.getReasons()).containsExactly(EntryErrorCode.BRONZE_REQUIRED.name());
		assertThat(withBronze.isCanEnter()).isTrue();
		assertThat(withBronze.getAvailableTicketBalance()).isEqualTo(2);
		assertThat(withBronze.getRemainingTicketLimit()).isEqualTo(1L);
		assertThat(entered.isCanEnter()).isFalse();
		assertThat(entered.getReasons()).containsExactly(EntryErrorCode.ALREADY_ENTERED.name());
		assertThat(entered.getUsedTicketCount()).isEqualTo(1);
		assertThat(entered.getRemainingTicketLimit()).isZero();
	}

	@Test
	@DisplayName("응모권 미사용 이벤트는 수량 값이 모두 0이고 응모하기 전에는 응모할 수 있으며 응모한 뒤에는 ALREADY_ENTERED다")
	void noTicketEventHasZeroQuantities() {
		// given
		UUID event = noTicketEvent();
		// when
		EntryEligibility before = check(userId, event);
		entryService.enter(command(userId, event, UUID.randomUUID(), tickets()));
		EntryEligibility after = check(userId, event);
		// then
		assertThat(before.isCanEnter()).isTrue();
		assertThat(before.getUsedTicketCount()).isZero();
		assertThat(before.getRemainingTicketLimit()).isZero();
		assertThat(before.getAvailableTicketBalance()).isZero();
		assertThat(after.getReasons()).containsExactly(EntryErrorCode.ALREADY_ENTERED.name());
	}

	@Test
	@DisplayName("마감·멤버십·관리자 사유는 함께 나오고 이벤트가 없거나 삭제됐으면 EVENT_NOT_FOUND다")
	void collectsAllReasonsAndRejectsMissingEvent() {
		// given
		giveTickets(userId, TicketGrade.BRONZE, 1);
		Instant endsAt = NOW.plusSeconds(60);
		UUID vipEvent = seeds.event("TICKET", true, 5, "vip", NOW.minusSeconds(3_600), endsAt, "OPEN", null);
		UUID deleted = seeds.event("TICKET", true, 5, "excellent", NOW.minusSeconds(3_600), endsAt, "OPEN",
				NOW.minusSeconds(10));
		clock.set(endsAt);
		// when
		EntryEligibility result = eligibilityService.check(userId, true, Membership.EXCELLENT, vipEvent);
		// then
		assertThat(result.isCanEnter()).isFalse();
		assertThat(result.getReasons()).containsExactly(EntryErrorCode.ADMIN_ENTRY_FORBIDDEN.name(),
				EntryErrorCode.ENTRY_MEMBERSHIP_NOT_MET.name(), EntryErrorCode.EVENT_NOT_OPEN.name());
		assertThatThrownBy(() -> check(userId, deleted))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_FOUND));
		assertThatThrownBy(() -> check(userId, UUID.randomUUID()))
				.satisfies(e -> assertEntryError(e, EntryErrorCode.EVENT_NOT_FOUND));
	}
}
