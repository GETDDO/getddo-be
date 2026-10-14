package com.getddo.db.ticket;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceClaim;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.GrantedTicket;
import com.getddo.core.ticket.domain.Ticket;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistory;
import com.getddo.core.ticket.repository.GrantSourceRepository;
import com.getddo.core.ticket.repository.TicketHistoryRepository;
import com.getddo.core.ticket.repository.TicketRepository;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 응모권 Entity 매핑과 저장소 구현을 Flyway로 만든 실제 MySQL 스키마에서 검증한다. */
class TicketRepositoryIntegrationTest extends TicketIntegrationTestSupport {

	private static final Instant GRANTED_AT = Instant.parse("2026-09-15T03:00:00.123456Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-09-30T15:00:00Z");

	@Autowired
	private TicketRepository ticketRepository;
	@Autowired
	private TicketHistoryRepository historyRepository;
	@Autowired
	private GrantSourceRepository grantSourceRepository;

	@Test
	@DisplayName("응모권과 지급 이력을 저장하면 UUID v7 id와 지급 당시 값이 그대로 저장된다")
	void savesTicketsAndHistories() {
		// given
		TicketGrantSeeds.AttendanceParents parents = seeds.attendanceParents(userId);
		// when
		List<Ticket> saved = transaction.execute(status -> {
			UUID claimId = seeds.attendanceClaim(userId, parents, 2);
			GrantSource source = new GrantSource(GrantSourceType.ATTENDANCE, claimId);
			List<Ticket> tickets = ticketRepository.saveAll(List.of(
					Ticket.issue(userId, source, TicketGrade.BRONZE, EXPIRES_AT, GRANTED_AT),
					Ticket.issue(userId, source, TicketGrade.BRONZE, EXPIRES_AT, GRANTED_AT)));
			historyRepository.saveAll(tickets.stream().map(ticket -> TicketHistory.grant(ticket, "출석 보상")).toList());
			return tickets;
		});
		// then
		assertThat(saved).hasSize(2).allSatisfy(ticket -> assertThat(ticket.getId().version()).isEqualTo(7));
		assertThat(ticketCount(userId)).isEqualTo(2);
		assertThat(historyCount(userId)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select created_at from tickets where id = ?", LocalDateTime.class,
				bytes(saved.get(0).getId()))).isEqualTo(LocalDateTime.parse("2026-09-15T03:00:00.123456"));
		assertThat(jdbc.queryForObject("select expires_at from tickets where id = ?", LocalDateTime.class,
				bytes(saved.get(0).getId()))).isEqualTo(LocalDateTime.parse("2026-09-30T15:00:00"));
		assertThat(jdbc.queryForObject("""
				select count(*) from ticket_histories
				where ticket_id = ? and operation_type = 'GRANT' and ticket_version = 1 and status = 'AVAILABLE'
				  and reason = '출석 보상' and created_at = '2026-09-15 03:00:00.123456'
				""", Long.class, bytes(saved.get(0).getId()))).isEqualTo(1L);
	}

	@Test
	@DisplayName("청구로 지급된 응모권을 지급 이력의 시각·만료 시각과 응모권의 등급으로 다시 읽는다")
	void findsGrantedTickets() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		GrantSource source = transaction.execute(status -> {
			GrantSource created = new GrantSource(GrantSourceType.MISSION, seeds.missionClaim(userId, parents, 1));
			List<Ticket> tickets = ticketRepository.saveAll(
					List.of(Ticket.issue(userId, created, TicketGrade.GOLD, EXPIRES_AT, GRANTED_AT)));
			historyRepository.saveAll(tickets.stream().map(ticket -> TicketHistory.grant(ticket, "미션")).toList());
			return created;
		});
		// when
		List<GrantedTicket> granted = transaction.execute(status -> ticketRepository.findGranted(source));
		// then
		assertThat(granted).singleElement().satisfies(ticket -> {
			assertThat(ticket.getGrade()).isEqualTo(TicketGrade.GOLD);
			assertThat(ticket.getGrantedAt()).isEqualTo(GRANTED_AT);
			assertThat(ticket.getExpiresAt()).isEqualTo(EXPIRES_AT);
		});
	}

	@Test
	@DisplayName("지급된 응모권이 없는 청구는 빈 목록이다")
	void findsNothingForUngrantedClaim() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = transaction.execute(status -> seeds.missionClaim(userId, parents, 1));
		// when
		List<GrantedTicket> granted = transaction.execute(status ->
				ticketRepository.findGranted(new GrantSource(GrantSourceType.MISSION, claimId)));
		// then
		assertThat(granted).isEmpty();
	}

	@Test
	@DisplayName("같은 미션 청구로 응모권을 두 번 저장하면 UNIQUE로 거부한다")
	void rejectsDuplicateMissionClaim() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = transaction.execute(status -> seeds.missionClaim(userId, parents, 1));
		GrantSource source = new GrantSource(GrantSourceType.MISSION, claimId);
		transaction.executeWithoutResult(status -> ticketRepository.saveAll(
				List.of(Ticket.issue(userId, source, TicketGrade.BRONZE, EXPIRES_AT, GRANTED_AT))));
		// when
		// then
		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> ticketRepository.saveAll(
				List.of(Ticket.issue(userId, source, TicketGrade.BRONZE, EXPIRES_AT, GRANTED_AT)))))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(ticketCount(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("다른 사용자의 청구로 응모권을 저장하면 청구·사용자 복합 FK로 거부한다")
	void rejectsClaimOwnedByAnotherUser() {
		// given
		UUID other = seeds.user();
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(other);
		UUID otherClaim = transaction.execute(status -> seeds.missionClaim(other, parents, 1));
		GrantSource source = new GrantSource(GrantSourceType.MISSION, otherClaim);
		// when
		// then
		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> ticketRepository.saveAll(
				List.of(Ticket.issue(userId, source, TicketGrade.BRONZE, EXPIRES_AT, GRANTED_AT)))))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(ticketCount(userId)).isZero();
	}

	@Test
	@DisplayName("지급 근거가 없는 응모권은 한 청구만 가리켜야 한다는 CHECK로 거부한다")
	void rejectsTicketWithoutOrWithManyClaims() {
		// given
		TicketGrantSeeds.MissionParents mission = seeds.missionParents(userId);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		UUID missionClaim = transaction.execute(status -> seeds.missionClaim(userId, mission, 1));
		UUID gameClaim = transaction.execute(status -> seeds.gameClaim(userId, game, 1));
		// when
		// then
		assertThatThrownBy(() -> jdbc.update("""
				insert into tickets (id, user_id, grade, status, expires_at, version, created_at, updated_at)
				values (?, ?, 'BRONZE', 'AVAILABLE', '2026-09-30 15:00:00', 1, '2026-09-15 03:00:00',
				  '2026-09-15 03:00:00')
				""", bytes(UUID.randomUUID()), bytes(userId))).isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbc.update("""
				insert into tickets (id, user_id, mission_reward_claim_id, game_reward_claim_id, grade, status,
				  expires_at, version, created_at, updated_at)
				values (?, ?, ?, ?, 'BRONZE', 'AVAILABLE', '2026-09-30 15:00:00', 1, '2026-09-15 03:00:00',
				  '2026-09-15 03:00:00')
				""", bytes(UUID.randomUUID()), bytes(userId), bytes(missionClaim), bytes(gameClaim)))
				.isInstanceOf(DataAccessException.class);
		assertThat(ticketCount(userId)).isZero();
	}

	@Test
	@DisplayName("같은 응모권의 같은 버전 이력은 UNIQUE로 거부한다")
	void rejectsDuplicateHistoryVersion() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		Ticket ticket = transaction.execute(status -> {
			GrantSource source = new GrantSource(GrantSourceType.MISSION, seeds.missionClaim(userId, parents, 1));
			Ticket saved = ticketRepository.saveAll(
					List.of(Ticket.issue(userId, source, TicketGrade.SILVER, EXPIRES_AT, GRANTED_AT))).get(0);
			historyRepository.saveAll(List.of(TicketHistory.grant(saved, "미션")));
			return saved;
		});
		// when
		// then
		assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
				historyRepository.saveAll(List.of(TicketHistory.grant(ticket, "미션")))))
				.isInstanceOf(DataAccessException.class);
		assertThat(historyCount(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 트랜잭션에서 방금 저장한 청구를 종류별로 조회하고 없는 청구는 빈 값을 반환한다")
	void findsClaimsOfEachType() {
		// given
		TicketGrantSeeds.MissionParents mission = seeds.missionParents(userId);
		TicketGrantSeeds.AttendanceParents attendance = seeds.attendanceParents(userId);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		// when
		// then
		transaction.executeWithoutResult(status -> {
			UUID missionClaim = seeds.missionClaim(userId, mission, 1);
			UUID attendanceClaim = seeds.attendanceClaim(userId, attendance, 3);
			UUID gameClaim = seeds.gameClaim(userId, game, 1);
			assertThat(grantSourceRepository.findForUpdate(new GrantSource(GrantSourceType.MISSION, missionClaim)))
					.get().usingRecursiveComparison().isEqualTo(new GrantSourceClaim(userId, 1));
			assertThat(grantSourceRepository.findForUpdate(new GrantSource(GrantSourceType.ATTENDANCE, attendanceClaim)))
					.get().usingRecursiveComparison().isEqualTo(new GrantSourceClaim(userId, 3));
			assertThat(grantSourceRepository.findForUpdate(new GrantSource(GrantSourceType.GAME, gameClaim)))
					.get().usingRecursiveComparison().isEqualTo(new GrantSourceClaim(userId, 1));
			assertThat(grantSourceRepository.findForUpdate(new GrantSource(GrantSourceType.GAME, missionClaim)))
					.isEmpty();
		});
	}

	@Test
	@DisplayName("게임 청구는 수량 1장만 허용하고 같은 게임 플레이로 두 번 청구할 수 없다")
	void gameClaimAllowsOneTicketAndOneClaimPerPlay() {
		// given
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		// when
		// then
		assertThatThrownBy(() -> seeds.gameClaim(userId, game, 2)).isInstanceOf(DataAccessException.class);
		seeds.gameClaim(userId, game, 1);
		assertThatThrownBy(() -> seeds.gameClaim(userId, game, 1)).isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uq_game_reward_claims_play");
	}

	@Test
	@DisplayName("미션·게임 보상 정책은 수량 1장만 허용하고 출석 보상 정책은 1장 이상을 허용한다")
	void rewardPolicyQuantityByType() {
		// given
		String insert = """
				insert into reward_policies (id, created_by, reward_type, game_id, reward_ticket_count,
				  effective_from, effective_until, created_at)
				values (?, ?, ?, null, ?, '2026-09-01 00:00:00', '2026-09-02 00:00:00', '2026-09-01 00:00:00')
				""";
		// when
		// then
		assertThatThrownBy(() -> jdbc.update(insert, bytes(UUID.randomUUID()), bytes(userId), "MISSION", 2))
				.isInstanceOf(DataAccessException.class);
		assertThat(jdbc.update(insert, bytes(UUID.randomUUID()), bytes(userId), "ATTENDANCE", 3)).isEqualTo(1);
	}
}
