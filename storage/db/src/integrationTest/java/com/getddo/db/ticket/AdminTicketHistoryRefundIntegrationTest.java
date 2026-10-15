package com.getddo.db.ticket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.ticket.domain.AdminTicketHistoryFilter;
import com.getddo.core.ticket.domain.AdminTicketHistoryView;
import com.getddo.core.ticket.domain.RefundCommand;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketRefundFilter;
import com.getddo.core.ticket.domain.TicketRefundView;
import com.getddo.core.ticket.domain.UseCommand;
import com.getddo.core.ticket.domain.UseSelection;
import com.getddo.core.ticket.repository.AdminTicketQueryRepository;
import com.getddo.core.ticket.service.TicketRefundService;
import com.getddo.core.ticket.service.TicketUseService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/** 관리자 처리 이력(AO05)·반환 이력(AO08) 조회 SQL을 Flyway로 만든 실제 MySQL 스키마에서 검증한다. */
class AdminTicketHistoryRefundIntegrationTest extends TicketIntegrationTestSupport {

	private static final Instant BASE = Instant.parse("2026-09-15T03:00:00Z");
	private static final PageQuery FIRST_PAGE = new PageQuery(1, 20);

	@Autowired
	private AdminTicketQueryRepository adminRepository;
	@Autowired
	private TicketUseService useService;
	@Autowired
	private TicketRefundService refundService;

	/** 사용자 소유 응모권 한 장(만료 한참 뒤)을 한 응모에 쓰고 반환까지 마친 결과. */
	private record UsedAndRefunded(UUID entryId, UUID eventId) {
	}

	private UsedAndRefunded useAndRefund(UUID user) {
		seeds.ticket(user, "GOLD", Instant.parse("2026-12-31T00:00:00Z"));
		UUID entry = seeds.eventEntry(user);
		UUID event = TicketGrantSeeds.uuid(jdbc.queryForObject("select event_id from event_entries where id = ?",
				byte[].class, bytes(entry)));
		clock.set(BASE);
		transaction.executeWithoutResult(status -> useService.use(
				new UseCommand(user, entry, List.of(new UseSelection(TicketGrade.GOLD, 1)), "테스트 응모")));
		clock.set(BASE.plusSeconds(60));
		transaction.executeWithoutResult(status -> refundService.refund(new RefundCommand(entry, "이벤트 취소")));
		return new UsedAndRefunded(entry, event);
	}

	private PageResult<AdminTicketHistoryView> histories(AdminTicketHistoryFilter filter) {
		return transaction.execute(status -> adminRepository.findHistories(filter, FIRST_PAGE));
	}

	private PageResult<TicketRefundView> refunds(TicketRefundFilter filter) {
		return transaction.execute(status -> adminRepository.findRefunds(filter, FIRST_PAGE));
	}

	private static AdminTicketHistoryFilter historyOf(UUID user, UUID event, UUID mission,
			TicketOperationType type, Instant from, Instant to) {
		return AdminTicketHistoryFilter.of(user, event, mission, type, from, to);
	}

	@Test
	@DisplayName("처리 이력에 사용자 이름과 이벤트 제목을 더하고 반환 이력의 이벤트는 되돌린 사용 이력의 응모에서 얻는다")
	void historiesCarryNamesAndRefundEvent() {
		// given
		jdbc.update("update users set name = ? where id = ?", "김응모", bytes(userId));
		UsedAndRefunded flow = useAndRefund(userId);
		// when
		PageResult<AdminTicketHistoryView> page = histories(historyOf(userId, null, null, null, null, null));
		// then
		assertThat(page.getItems()).extracting(view -> view.getHistory().getOperationType())
				.containsExactly(TicketOperationType.REFUND, TicketOperationType.USE);
		assertThat(page.getItems()).allSatisfy(view -> {
			assertThat(view.getUserId()).isEqualTo(userId);
			assertThat(view.getUserName()).isEqualTo("김응모");
			assertThat(view.getEventTitle()).isEqualTo("테스트 이벤트");
			assertThat(view.getHistory().getEventId()).isEqualTo(flow.eventId());
			assertThat(view.getMissionTitle()).isNotNull();
		});
		assertThat(page.getItems().get(0).getHistory().getOriginalUseHistoryId())
				.isEqualTo(page.getItems().get(1).getHistory().getId());
	}

	@Test
	@DisplayName("eventId·operationType·missionId·기간으로 처리 이력을 좁힌다")
	void filtersHistories() {
		// given
		UsedAndRefunded flow = useAndRefund(userId);
		UUID missionId = TicketGrantSeeds.uuid(jdbc.queryForObject("""
				select mc.mission_id from tickets t join mission_reward_claims mc on mc.id = t.mission_reward_claim_id
				where t.user_id = ?
				""", byte[].class, bytes(userId)));
		UUID otherUser = seeds.user();
		useAndRefund(otherUser);
		// when
		PageResult<AdminTicketHistoryView> byEvent = histories(historyOf(null, flow.eventId(), null, null, null, null));
		PageResult<AdminTicketHistoryView> usesOnly = histories(
				historyOf(userId, null, null, TicketOperationType.USE, null, null));
		PageResult<AdminTicketHistoryView> byMission = histories(historyOf(null, null, missionId, null, null, null));
		PageResult<AdminTicketHistoryView> otherMission = histories(
				historyOf(userId, null, UUID.randomUUID(), null, null, null));
		PageResult<AdminTicketHistoryView> fromRefund = histories(
				historyOf(userId, null, null, null, BASE.plusSeconds(60), null));
		PageResult<AdminTicketHistoryView> beforeRefund = histories(
				historyOf(userId, null, null, null, null, BASE.plusSeconds(60)));
		// then
		assertThat(byEvent.getTotalElements()).isEqualTo(2);
		assertThat(byEvent.getItems()).allMatch(view -> view.getUserId().equals(userId));
		assertThat(usesOnly.getItems()).singleElement()
				.satisfies(view -> assertThat(view.getHistory().getOperationType()).isEqualTo(TicketOperationType.USE));
		assertThat(byMission.getItems()).isNotEmpty().allMatch(view -> view.getUserId().equals(userId));
		assertThat(otherMission.getTotalElements()).isZero();
		assertThat(fromRefund.getItems()).singleElement().satisfies(
				view -> assertThat(view.getHistory().getOperationType()).isEqualTo(TicketOperationType.REFUND));
		assertThat(beforeRefund.getItems()).singleElement().satisfies(
				view -> assertThat(view.getHistory().getOperationType()).isEqualTo(TicketOperationType.USE));
	}

	@Test
	@DisplayName("반환 이력 한 건이 한 행이고 사용 이력·응모권·이벤트·사용자 정보를 채운다")
	void refundsListOneRowPerRefundHistory() {
		// given
		jdbc.update("update users set name = ? where id = ?", "박반환", bytes(userId));
		UsedAndRefunded flow = useAndRefund(userId);
		UUID useHistory = TicketGrantSeeds.uuid(jdbc.queryForObject(
				"select h.id from ticket_histories h join tickets t on t.id = h.ticket_id "
						+ "where t.user_id = ? and h.operation_type = 'USE'", byte[].class, bytes(userId)));
		// when
		PageResult<TicketRefundView> page = refunds(TicketRefundFilter.of(null, userId, null, null));
		// then
		assertThat(page.getTotalElements()).isEqualTo(1);
		TicketRefundView refund = page.getItems().get(0);
		assertThat(refund.getSpendHistoryId()).isEqualTo(useHistory);
		assertThat(refund.getEventId()).isEqualTo(flow.eventId());
		assertThat(refund.getEventTitle()).isEqualTo("테스트 이벤트");
		assertThat(refund.getUserId()).isEqualTo(userId);
		assertThat(refund.getUserName()).isEqualTo("박반환");
		assertThat(refund.getGrade()).isEqualTo(TicketGrade.GOLD);
		assertThat(refund.getRefundedAt()).isEqualTo(BASE.plusSeconds(60));
		assertThat(refund.getTargetExpiresAt()).isNotNull();
	}

	@Test
	@DisplayName("반환 이력을 eventId·userId·기간으로 좁히고 반환되지 않은 사용은 나오지 않는다")
	void filtersRefunds() {
		// given
		UsedAndRefunded flow = useAndRefund(userId);
		UUID other = seeds.user();
		seeds.ticket(other, "SILVER", Instant.parse("2026-12-31T00:00:00Z"));
		UUID unreturnedEntry = seeds.eventEntry(other);
		transaction.executeWithoutResult(status -> useService.use(new UseCommand(other, unreturnedEntry,
				List.of(new UseSelection(TicketGrade.SILVER, 1)), "테스트 응모")));
		// when
		PageResult<TicketRefundView> byEvent = refunds(TicketRefundFilter.of(flow.eventId(), null, null, null));
		PageResult<TicketRefundView> byOther = refunds(TicketRefundFilter.of(null, other, null, null));
		PageResult<TicketRefundView> at = refunds(
				TicketRefundFilter.of(null, userId, BASE.plusSeconds(60), BASE.plusSeconds(61)));
		PageResult<TicketRefundView> before = refunds(
				TicketRefundFilter.of(null, userId, null, BASE.plusSeconds(60)));
		// then
		assertThat(byEvent.getItems()).singleElement()
				.satisfies(refund -> assertThat(refund.getUserId()).isEqualTo(userId));
		assertThat(byOther.getTotalElements()).isZero();
		assertThat(at.getTotalElements()).isEqualTo(1);
		assertThat(before.getTotalElements()).isZero();
	}

	@Test
	@DisplayName("처리 이력은 지급 이력도 포함하며 이벤트가 없는 지급은 이벤트 제목이 null이고 미션 제목을 채운다")
	void grantsRemainVisibleInHistories() {
		// given
		grantNewMissionClaim(userId, 1);
		// when
		PageResult<AdminTicketHistoryView> grants = histories(
				historyOf(userId, null, null, TicketOperationType.GRANT, null, null));
		// then
		assertThat(grants.getItems()).singleElement().satisfies(view -> {
			assertThat(view.getEventTitle()).isNull();
			assertThat(view.getMissionTitle()).isNotNull();
		});
	}
}
