package com.getddo.db.ticket;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketGrade;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;

/** 실제 MySQL에서 지급 한 건이 응모권·이력에 남기는 결과와 그 정합성을 검증한다. */
class TicketGrantServiceIntegrationTest extends TicketIntegrationTestSupport {

	@Test
	@DisplayName("출석 2장 지급은 응모권 2장과 지급 이력 2건을 만들고 시각이 모두 지급 시각과 같다")
	void attendanceGrantCreatesTicketsAndHistories() {
		// given: 시계를 읽을 때마다 1ms씩 진행해 시각을 두 번 이상 읽으면 값이 달라지게 한다
		clock.tickEveryRead(true);
		TicketGrantSeeds.AttendanceParents parents = seeds.attendanceParents(userId);
		// when
		GrantResult result = transaction.execute(status -> {
			UUID claimId = seeds.attendanceClaim(userId, parents, 2);
			return grantService.grant(command(userId, GrantSourceType.ATTENDANCE, claimId, 2));
		});
		// then
		assertThat(result.isReplayed()).isFalse();
		assertThat(result.getQuantity()).isEqualTo(2);
		assertThat(result.getGrade()).isEqualTo(TicketGrade.BRONZE);
		assertThat(ticketCount(userId)).isEqualTo(2);
		assertThat(historyCount(userId)).isEqualTo(2);

		Instant grantedAt = result.getGrantedAt();
		assertThat(jdbc.queryForList("select created_at from tickets where user_id = ?", java.time.LocalDateTime.class,
				bytes(userId))).extracting(value -> value.toInstant(java.time.ZoneOffset.UTC)).containsOnly(grantedAt);
		assertThat(jdbc.queryForList("select updated_at from tickets where user_id = ?", java.time.LocalDateTime.class,
				bytes(userId))).extracting(value -> value.toInstant(java.time.ZoneOffset.UTC)).containsOnly(grantedAt);
		assertThat(jdbc.queryForList("""
				select h.created_at from ticket_histories h join tickets t on t.id = h.ticket_id
				where t.user_id = ?
				""", java.time.LocalDateTime.class, bytes(userId)))
				.extracting(value -> value.toInstant(java.time.ZoneOffset.UTC)).containsOnly(grantedAt);
	}

	@Test
	@DisplayName("응모권은 사용 가능·버전 1로, 지급 이력은 GRANT·버전 1·같은 상태와 만료 시각으로 쌓인다")
	void ticketAndHistoryMatch() {
		// given
		// when
		GrantResult result = grantNewMissionClaim(userId, 1);
		// then
		assertThat(count("""
				select count(*) from tickets
				where user_id = ? and status = 'AVAILABLE' and version = 1 and mission_reward_claim_id is not null
				  and attendance_reward_claim_id is null and game_reward_claim_id is null
				""", bytes(userId))).isEqualTo(1);
		assertThat(count("""
				select count(*) from ticket_histories h join tickets t on t.id = h.ticket_id
				where t.user_id = ? and h.operation_type = 'GRANT' and h.ticket_version = t.version
				  and h.status = t.status and h.expires_at = t.expires_at and h.reason = '테스트 보상'
				  and h.event_entry_id is null and h.original_use_history_id is null
				  and h.corrected_history_id is null
				""", bytes(userId))).isEqualTo(1);
		assertThat(utc("select expires_at from tickets where user_id = ?", bytes(userId)))
				.isEqualTo(result.getExpiresAt());
		assertThat(jdbc.queryForObject("select grade from tickets where user_id = ?", String.class, bytes(userId)))
				.isEqualTo(result.getGrade().name());
	}

	@Test
	@DisplayName("같은 청구로 다시 호출하면 추가 지급 없이 지급 당시 등급·시각을 replayed=true로 반환한다")
	void replayDoesNotCreateTickets() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = transaction.execute(status -> seeds.missionClaim(userId, parents, 1));
		GrantResult first = transaction.execute(status ->
				grantService.grant(command(userId, GrantSourceType.MISSION, claimId, 1)));
		clock.set(TicketIntegrationTestApplication.INITIAL_TIME.plusSeconds(60));
		// when
		GrantResult replayed = transaction.execute(status ->
				grantService.grant(command(userId, GrantSourceType.MISSION, claimId, 1)));
		// then
		assertThat(replayed).usingRecursiveComparison().isEqualTo(new GrantResult(1, first.getGrade(),
				first.getGrantedAt(), first.getExpiresAt(), true));
		assertThat(ticketCount(userId)).isEqualTo(1);
		assertThat(historyCount(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("KST 9/30 23:59:59.999와 10/1 00:00:00 지급은 서로 다른 만료 시각을 가진다")
	void monthBoundaryUsesDifferentExpiry() {
		// given
		clock.set(Instant.parse("2026-09-30T14:59:59.999Z"));
		GrantResult september = grantNewMissionClaim(userId, 1);
		clock.set(Instant.parse("2026-09-30T15:00:00Z"));
		// when
		GrantResult october = grantNewMissionClaim(userId, 1);
		// then
		assertThat(september.getExpiresAt()).isEqualTo(Instant.parse("2026-09-30T15:00:00Z"));
		assertThat(october.getExpiresAt()).isEqualTo(Instant.parse("2026-10-31T15:00:00Z"));
		assertThat(ticketCount(userId)).isEqualTo(2);
	}

	@Test
	@DisplayName("시계가 나노초를 주어도 지급 시각은 DB가 저장하는 마이크로초로 잘라 응답·재조회·저장 값이 같다")
	void grantedAtIsTruncatedToStoredPrecision() {
		// given
		clock.set(Instant.parse("2026-09-15T03:00:00.123456789Z"));
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = transaction.execute(status -> seeds.missionClaim(userId, parents, 1));
		// when
		GrantResult granted = transaction.execute(status ->
				grantService.grant(command(userId, GrantSourceType.MISSION, claimId, 1)));
		// then
		Instant truncated = Instant.parse("2026-09-15T03:00:00.123456Z");
		assertThat(granted.getGrantedAt()).isEqualTo(truncated);
		assertThat(grantService.findGrant(new GrantSource(GrantSourceType.MISSION, claimId)))
				.map(GrantResult::getGrantedAt).contains(truncated);
		assertThat(utc("select created_at from tickets where user_id = ?", bytes(userId))).isEqualTo(truncated);
	}

	@Test
	@DisplayName("9월 마지막 1마이크로초 안의 지급도 9월 만료이고 생성 시각이 만료 시각으로 반올림되지 않는다")
	void lastInstantOfMonthStaysInMonth() {
		// given: 반올림하면 10/1 00:00 KST가 되는 순간
		clock.set(Instant.parse("2026-09-30T14:59:59.9999996Z"));
		// when
		GrantResult granted = grantNewMissionClaim(userId, 1);
		// then
		Instant lastMicro = Instant.parse("2026-09-30T14:59:59.999999Z");
		assertThat(granted.getGrantedAt()).isEqualTo(lastMicro);
		assertThat(granted.getExpiresAt()).isEqualTo(Instant.parse("2026-09-30T15:00:00Z"));
		assertThat(utc("select created_at from tickets where user_id = ?", bytes(userId)))
				.isEqualTo(lastMicro)
				.isBefore(granted.getExpiresAt());
	}

	@Test
	@DisplayName("청구 종류마다 응모권의 해당 청구 참조 컬럼 하나만 채워진다")
	void fillsOnlyMatchingClaimColumn() {
		// given
		TicketGrantSeeds.MissionParents mission = seeds.missionParents(userId);
		TicketGrantSeeds.AttendanceParents attendance = seeds.attendanceParents(userId);
		TicketGrantSeeds.GameParents game = seeds.gameParents(userId);
		// when
		transaction.executeWithoutResult(status -> {
			UUID missionClaim = seeds.missionClaim(userId, mission, 1);
			UUID attendanceClaim = seeds.attendanceClaim(userId, attendance, 1);
			UUID gameClaim = seeds.gameClaim(userId, game, 1);
			grantService.grant(command(userId, GrantSourceType.MISSION, missionClaim, 1));
			grantService.grant(command(userId, GrantSourceType.ATTENDANCE, attendanceClaim, 1));
			grantService.grant(command(userId, GrantSourceType.GAME, gameClaim, 1));
		});
		// then
		assertThat(filledClaimColumns()).containsExactlyInAnyOrder("MISSION", "ATTENDANCE", "GAME");
	}

	@Test
	@DisplayName("출석 응모권은 항상 브론즈이고 미션·게임 응모권은 세 등급 중 하나다")
	void gradesFollowSourceType() {
		// given
		TicketGrantSeeds.AttendanceParents attendance = seeds.attendanceParents(userId);
		// when
		GrantResult attendanceGrant = transaction.execute(status -> {
			UUID claim = seeds.attendanceClaim(userId, attendance, 1);
			return grantService.grant(command(userId, GrantSourceType.ATTENDANCE, claim, 1));
		});
		GrantResult missionGrant = grantNewMissionClaim(userId, 1);
		// then
		assertThat(attendanceGrant.getGrade()).isEqualTo(TicketGrade.BRONZE);
		assertThat(missionGrant.getGrade()).isIn((Object[]) TicketGrade.values());
	}

	@Test
	@DisplayName("findGrant는 지급 전 빈 값, 지급 후 같은 결과를 replayed=true로 트랜잭션 유무와 관계없이 반환한다")
	void findGrantWithAndWithoutTransaction() {
		// given
		TicketGrantSeeds.MissionParents parents = seeds.missionParents(userId);
		UUID claimId = transaction.execute(status -> seeds.missionClaim(userId, parents, 1));
		GrantSource source = new GrantSource(GrantSourceType.MISSION, claimId);
		assertThat(grantService.findGrant(source)).isEmpty();
		GrantResult granted = transaction.execute(status ->
				grantService.grant(command(userId, GrantSourceType.MISSION, claimId, 1)));
		GrantResult expected = new GrantResult(1, granted.getGrade(), granted.getGrantedAt(), granted.getExpiresAt(),
				true);
		// when
		Optional<GrantResult> withoutTransaction = grantService.findGrant(source);
		Optional<GrantResult> inTransaction = transaction.execute(status -> grantService.findGrant(source));
		// then
		assertThat(withoutTransaction).get().usingRecursiveComparison().isEqualTo(expected);
		assertThat(inTransaction).get().usingRecursiveComparison().isEqualTo(expected);
	}

	private List<String> filledClaimColumns() {
		return jdbc.queryForList("""
				select concat_ws(',',
				  if(mission_reward_claim_id is null, null, 'MISSION'),
				  if(attendance_reward_claim_id is null, null, 'ATTENDANCE'),
				  if(game_reward_claim_id is null, null, 'GAME'))
				from tickets where user_id = ?
				""", String.class, bytes(userId));
	}
}
