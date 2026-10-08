package com.getddo.db.ticket;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 응모권 통합 테스트용 최소 시드 행.
 *
 * <p>청구 테이블의 FK를 끄지 않고 부모 행(사용자, 보상 정책, 미션·제출, 출석, 게임·플레이)을 실제로 넣는다.
 * 보상 정책은 종료 시각을 채워 "열린 정책은 종류별 하나" 제약({@code open_guard})에 걸리지 않게 한다.
 * {@code *Parents}는 트랜잭션 밖에서 호출해 미리 커밋하고, {@code *Claim}은 지급과 같은 트랜잭션에서 호출한다.</p>
 */
public class TicketGrantSeeds {

	private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 9, 1, 0, 0);
	private static final LocalDate FIRST_DATE = LocalDate.of(2026, 9, 1);

	private final JdbcTemplate jdbc;
	private final AtomicInteger sequence = new AtomicInteger();
	private final List<UUID> users = new CopyOnWriteArrayList<>();
	private final List<UUID> games = new CopyOnWriteArrayList<>();

	public TicketGrantSeeds(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 이 헬퍼로 만든 사용자와 그 사용자의 응모권·청구·부모 행을 FK 역순으로 지운다.
	 * 테스트가 커밋한 행을 다음 테스트에 남기지 않으려고 {@code @AfterEach}에서 호출한다.
	 */
	public void cleanUp() {
		// 1) 사용자가 소유한 행. 다른 사용자가 만든 정책을 참조할 수 있으므로 정책보다 먼저 모두 지운다.
		for (UUID user : users) {
			byte[] id = bytes(user);
			jdbc.update("""
					delete a from ticket_ledger_allocations a
					join ticket_ledger l on l.id = a.ledger_id
					where l.user_id = ?
					""", id);
			jdbc.update("delete from ticket_ledger where user_id = ?", id);
			jdbc.update("delete from ticket_wallets where user_id = ?", id);
			jdbc.update("delete from mission_reward_claims where user_id = ?", id);
			jdbc.update("delete from attendance_reward_claims where user_id = ?", id);
			jdbc.update("delete from game_reward_claims where user_id = ?", id);
			jdbc.update("delete from mission_submissions where user_id = ?", id);
			jdbc.update("delete from attendance_streaks where user_id = ?", id);
			jdbc.update("delete from attendances where user_id = ?", id);
			jdbc.update("delete from game_plays where user_id = ?", id);
		}
		// 2) 사용자가 등록자인 행(미션·보상 정책·연속 출석 정책)
		for (UUID user : users) {
			byte[] id = bytes(user);
			jdbc.update("delete from missions where created_by = ?", id);
			jdbc.update("delete from reward_policies where created_by = ?", id);
			jdbc.update("""
					delete p from attendance_streak_policies p
					join attendance_streak_policy_sets s on s.id = p.policy_set_id
					where s.created_by = ?
					""", id);
			jdbc.update("delete from attendance_streak_policy_sets where created_by = ?", id);
		}
		for (UUID game : games) {
			jdbc.update("delete from games where id = ?", bytes(game));
		}
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", bytes(user));
		}
		users.clear();
		games.clear();
	}

	/** 호출자의 "기존 청구 조회" 단계. 미션 청구 UNIQUE 키 {@code (user_id, mission_id)}로 찾는다. */
	public Optional<UUID> findMissionClaim(UUID userId, UUID missionId) {
		List<byte[]> ids = jdbc.query(
				"select id from mission_reward_claims where user_id = ? and mission_id = ?",
				(row, index) -> row.getBytes(1), bytes(userId), bytes(missionId));
		return ids.stream().findFirst().map(TicketGrantSeeds::uuid);
	}

	public record MissionParents(UUID policyId, UUID missionId, UUID submissionId) {
	}

	public record AttendanceParents(UUID policyId, UUID attendanceId) {
	}

	public record GameParents(UUID gameId, UUID policyId, UUID playId) {
	}

	public UUID user() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into users (id, name, role, status, created_at, updated_at)
				values (?, ?, 'USER', 'ACTIVE', ?, ?)
				""", bytes(id), "user-" + next(), SEED_TIME, SEED_TIME);
		users.add(id);
		return id;
	}

	public MissionParents missionParents(UUID userId) {
		UUID policyId = rewardPolicy(userId, "MISSION", null);
		UUID missionId = UUID.randomUUID();
		jdbc.update("""
				insert into missions (id, reward_policy_id, created_by, title, description, mission_type,
				  starts_at, ends_at, status, created_at, updated_at)
				values (?, ?, ?, '테스트 미션', '설명', 'SURVEY', ?, ?, 'ACTIVE', ?, ?)
				""", bytes(missionId), bytes(policyId), bytes(userId),
				SEED_TIME, SEED_TIME.plusMonths(3), SEED_TIME, SEED_TIME);
		UUID submissionId = UUID.randomUUID();
		jdbc.update("""
				insert into mission_submissions (id, mission_id, user_id, reward_policy_id, idempotency_key,
				  received_at, is_completed, created_at)
				values (?, ?, ?, ?, ?, ?, true, ?)
				""", bytes(submissionId), bytes(missionId), bytes(userId), bytes(policyId),
				"submission-" + next(), SEED_TIME, SEED_TIME);
		return new MissionParents(policyId, missionId, submissionId);
	}

	/** 미션 보상 청구. 같은 부모로 두 번 넣으면 {@code UNIQUE (user_id, mission_id)}에 걸린다. */
	public UUID missionClaim(UUID userId, MissionParents parents, int ticketCount) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into mission_reward_claims (id, user_id, reward_policy_id, mission_id, mission_submission_id,
				  source_key, ticket_count, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?)
				""", bytes(id), bytes(userId), bytes(parents.policyId()), bytes(parents.missionId()),
				bytes(parents.submissionId()), "mission-" + next(), ticketCount, SEED_TIME);
		return id;
	}

	public AttendanceParents attendanceParents(UUID userId) {
		UUID policyId = rewardPolicy(userId, "ATTENDANCE", null);
		UUID attendanceId = UUID.randomUUID();
		jdbc.update("""
				insert into attendances (id, user_id, attendance_date, created_at)
				values (?, ?, ?, ?)
				""", bytes(attendanceId), bytes(userId), FIRST_DATE.plusDays(next()), SEED_TIME);
		return new AttendanceParents(policyId, attendanceId);
	}

	/** 출석 일일 보상 청구. */
	public UUID attendanceClaim(UUID userId, AttendanceParents parents, int ticketCount) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into attendance_reward_claims (id, user_id, attendance_id, reward_type, reward_policy_id,
				  reward_date, source_key, ticket_count, created_at)
				values (?, ?, ?, 'DAILY', ?, ?, ?, ?, ?)
				""", bytes(id), bytes(userId), bytes(parents.attendanceId()), bytes(parents.policyId()),
				FIRST_DATE, "attendance-" + next(), ticketCount, SEED_TIME);
		return id;
	}

	public GameParents gameParents(UUID userId) {
		UUID gameId = UUID.randomUUID();
		jdbc.update("""
				insert into games (id, code, name, rules, rule_version, created_at, updated_at)
				values (?, ?, '테스트 게임', '{}', 'v1', ?, ?)
				""", bytes(gameId), "game-" + next(), SEED_TIME, SEED_TIME);
		games.add(gameId);
		UUID policyId = rewardPolicy(userId, "GAME", gameId);
		UUID playId = UUID.randomUUID();
		jdbc.update("""
				insert into game_plays (id, game_id, user_id, play_token, rule_version, status, created_at)
				values (?, ?, ?, ?, 'v1', 'VALID', ?)
				""", bytes(playId), bytes(gameId), bytes(userId), "play-" + next(), SEED_TIME);
		return new GameParents(gameId, policyId, playId);
	}

	/** 게임 보상 청구. 호출마다 보상 기준일이 달라 {@code UNIQUE (user_id, game_id, reward_date)}에 걸리지 않는다. */
	public UUID gameClaim(UUID userId, GameParents parents, int ticketCount) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into game_reward_claims (id, user_id, game_id, game_play_id, reward_policy_id,
				  reward_date, source_key, ticket_count, created_at)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", bytes(id), bytes(userId), bytes(parents.gameId()), bytes(parents.playId()),
				bytes(parents.policyId()), FIRST_DATE.plusDays(next()), "game-" + next(), ticketCount, SEED_TIME);
		return id;
	}

	private UUID rewardPolicy(UUID createdBy, String rewardType, UUID gameId) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into reward_policies (id, created_by, reward_type, game_id, reward_ticket_count,
				  effective_from, effective_until, created_at)
				values (?, ?, ?, ?, 1, ?, ?, ?)
				""", bytes(id), bytes(createdBy), rewardType, gameId == null ? null : bytes(gameId),
				SEED_TIME.plusSeconds(next()), SEED_TIME.plusYears(1), SEED_TIME);
		return id;
	}

	private int next() {
		return sequence.incrementAndGet();
	}

	public static byte[] bytes(UUID value) {
		return ByteBuffer.allocate(16)
				.putLong(value.getMostSignificantBits())
				.putLong(value.getLeastSignificantBits())
				.array();
	}

	public static UUID uuid(byte[] value) {
		ByteBuffer buffer = ByteBuffer.wrap(value);
		return new UUID(buffer.getLong(), buffer.getLong());
	}
}
