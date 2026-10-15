package com.getddo.db.ticket;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketConsistencyResult;
import com.getddo.core.ticket.domain.TicketInconsistencyView;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.AdminTicketQueryRepository;
import com.getddo.core.ticket.service.AdminTicketQueryService;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 응모권 정합성 조회(AO04·AO09)를 실제 MySQL에서 검증한다. 어긋남은 현재 상태를 직접 바꿔 만든다. */
class AdminTicketInconsistencyIntegrationTest extends TicketIntegrationTestSupport {

	private static final PageQuery FIRST_PAGE = new PageQuery(1, 50);

	@Autowired
	private AdminTicketQueryRepository adminRepository;
	@Autowired
	private AdminTicketQueryService service;

	private final User admin = new User(UUID.randomUUID(), "관리자", UserRole.ADMIN, UserStatus.ACTIVE, null, null,
			null, null, null, null, null);

	/** 지급으로 만든 응모권의 ID. 지급 직후는 버전 1·AVAILABLE이라 이력과 일치한다. */
	private UUID grantedTicket(UUID user) {
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(user);
		UUID claimId = transaction.execute(status -> {
			UUID id = seeds.missionClaim(user, parents, 1);
			grantService.grant(command(user, GrantSourceType.MISSION, id, 1));
			return id;
		});
		return TicketGrantSeeds.uuid(jdbc.queryForObject("select id from tickets where mission_reward_claim_id = ?",
				byte[].class, bytes(claimId)));
	}

	@Test
	@DisplayName("이력과 일치하는 응모권만 있으면 일치로 보고 사용 가능 수를 센다")
	void consistentWhenStateMatchesLatestHistory() {
		// given
		grantedTicket(userId);
		grantedTicket(userId);
		// when
		TicketConsistencyResult result = service.getConsistency(admin, userId);
		// then
		assertThat(result.isConsistent()).isTrue();
		assertThat(result.getInconsistencies()).isEmpty();
		assertThat(result.getAvailableCount()).isEqualTo(2);
		assertThat(result.getUserId()).isEqualTo(userId);
	}

	@Test
	@DisplayName("현재 버전이나 상태가 마지막 이력과 다른 응모권만 어긋난 것으로 보고 버전·상태를 둘 다 알려 준다")
	void reportsVersionAndStatusMismatch() {
		// given
		UUID versionOnly = grantedTicket(userId);
		UUID statusOnly = grantedTicket(userId);
		UUID healthy = grantedTicket(userId);
		jdbc.update("update tickets set version = 3 where id = ?", bytes(versionOnly));
		jdbc.update("update tickets set status = 'SPENT' where id = ?", bytes(statusOnly));
		// when
		TicketConsistencyResult result = service.getConsistency(admin, userId);
		// then
		assertThat(result.isConsistent()).isFalse();
		assertThat(result.getInconsistencies()).extracting(TicketInconsistencyView::getTicketId)
				.containsExactlyInAnyOrder(versionOnly, statusOnly).doesNotContain(healthy);
		TicketInconsistencyView version = result.getInconsistencies().stream()
				.filter(view -> view.getTicketId().equals(versionOnly)).findFirst().orElseThrow();
		assertThat(version.getTicketVersion()).isEqualTo(3);
		assertThat(version.getLatestHistoryVersion()).isEqualTo(1);
		assertThat(version.getTicketStatus()).isEqualTo(TicketStatus.AVAILABLE);
		assertThat(version.getLatestHistoryStatus()).isEqualTo(TicketStatus.AVAILABLE);
		TicketInconsistencyView status = result.getInconsistencies().stream()
				.filter(view -> view.getTicketId().equals(statusOnly)).findFirst().orElseThrow();
		assertThat(status.getTicketStatus()).isEqualTo(TicketStatus.SPENT);
		assertThat(status.getLatestHistoryStatus()).isEqualTo(TicketStatus.AVAILABLE);
	}

	@Test
	@DisplayName("마지막 이력은 버전이 가장 큰 이력이고 이력이 하나도 없는 응모권은 버전 0·상태 null로 어긋난다")
	void usesHighestVersionHistoryAndFlagsMissingHistory() {
		// given
		UUID ticket = grantedTicket(userId);
		jdbc.update("""
				insert into ticket_histories (id, ticket_id, operation_type, status, ticket_version, expires_at, reason,
				  created_at)
				select ?, h.ticket_id, 'EXPIRE', 'EXPIRED', 2, h.expires_at, 'x', h.created_at
				from ticket_histories h where h.ticket_id = ?
				""", bytes(UUID.randomUUID()), bytes(ticket));
		jdbc.update("update tickets set version = 2, status = 'EXPIRED' where id = ?", bytes(ticket));
		UUID bare = insertTicket(userId, seeds.missionClaim(userId, seeds.missionParents(userId), 1), TicketGrade.BRONZE,
				TicketStatus.AVAILABLE, "2026-12-31T00:00:00Z");
		// when
		TicketConsistencyResult result = service.getConsistency(admin, userId);
		// then
		assertThat(result.getInconsistencies()).singleElement().satisfies(view -> {
			assertThat(view.getTicketId()).isEqualTo(bare);
			assertThat(view.getLatestHistoryVersion()).isZero();
			assertThat(view.getLatestHistoryStatus()).isNull();
		});
	}

	@Test
	@DisplayName("없는 사용자는 TICKET-011로 거절하고 관리자가 아니면 TICKET-010으로 거절한다")
	void rejectsUnknownUserAndNonAdmin() {
		// given
		User plain = new User(UUID.randomUUID(), "일반", UserRole.USER, UserStatus.ACTIVE, null, null, null, null,
				null, null, null);
		// when
		// then
		assertThatThrownBy(() -> service.getConsistency(admin, UUID.randomUUID()))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_USER_NOT_FOUND));
		assertThatThrownBy(() -> service.getConsistency(plain, userId))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_ADMIN_REQUIRED));
	}

	@Test
	@DisplayName("전체 조회는 사용자별로 좁히고 쪽 나누며 사용자 이름을 채운다")
	void pagesAcrossUsers() {
		// given
		jdbc.update("update users set name = ? where id = ?", "이어긋", bytes(userId));
		UUID mine1 = grantedTicket(userId);
		UUID mine2 = grantedTicket(userId);
		UUID other = seeds.user();
		UUID theirs = grantedTicket(other);
		for (UUID ticket : List.of(mine1, mine2, theirs)) {
			jdbc.update("update tickets set version = 5 where id = ?", bytes(ticket));
		}
		// when
		PageResult<TicketInconsistencyView> mine = transaction.execute(
				status -> adminRepository.findInconsistencies(userId, FIRST_PAGE));
		PageResult<TicketInconsistencyView> all = transaction.execute(
				status -> adminRepository.findInconsistencies(null, FIRST_PAGE));
		PageResult<TicketInconsistencyView> firstOfOne = transaction.execute(
				status -> adminRepository.findInconsistencies(userId, new PageQuery(1, 1)));
		PageResult<TicketInconsistencyView> secondOfOne = transaction.execute(
				status -> adminRepository.findInconsistencies(userId, new PageQuery(2, 1)));
		// then
		assertThat(mine.getTotalElements()).isEqualTo(2);
		assertThat(mine.getItems()).extracting(TicketInconsistencyView::getUserName).containsOnly("이어긋");
		assertThat(all.getItems()).extracting(TicketInconsistencyView::getTicketId).contains(mine1, mine2, theirs);
		assertThat(firstOfOne.getItems()).hasSize(1);
		assertThat(secondOfOne.getItems()).hasSize(1);
		assertThat(firstOfOne.getItems().get(0).getTicketId()).isNotEqualTo(secondOfOne.getItems().get(0).getTicketId());
		assertThat(firstOfOne.getTotalElements()).isEqualTo(2);
	}

	@Test
	@DisplayName("어긋남이 없으면 전체 조회는 빈 페이지다")
	void emptyWhenNothingMismatched() {
		// given
		grantedTicket(userId);
		// when
		PageResult<TicketInconsistencyView> mine = transaction.execute(
				status -> adminRepository.findInconsistencies(userId, FIRST_PAGE));
		// then
		assertThat(mine.getItems()).isEmpty();
		assertThat(mine.getTotalElements()).isZero();
	}
}
