package com.getddo.db.ticket;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
	private final List<UUID> events = new CopyOnWriteArrayList<>();

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
			// 반환·정정 이력은 같은 응모권의 다른 이력을 참조하므로 참조하는 쪽을 먼저 지운다.
			jdbc.update("""
					delete h from ticket_histories h
					join tickets t on t.id = h.ticket_id
					where t.user_id = ? and (h.original_use_history_id is not null or h.corrected_history_id is not null)
					""", id);
			jdbc.update("""
					delete h from ticket_histories h
					join tickets t on t.id = h.ticket_id
					where t.user_id = ?
					""", id);
			jdbc.update("delete from tickets where user_id = ?", id);
			jdbc.update("delete from event_entries where user_id = ?", id);
			jdbc.update("delete from event_participants where user_id = ?", id);
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
		for (UUID event : events) {
			jdbc.update("delete from events where id = ?", bytes(event));
		}
		for (UUID user : users) {
			jdbc.update("delete from users where id = ?", bytes(user));
		}
		users.clear();
		games.clear();
		events.clear();
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
				insert into mission_submissions (id, mission_id, user_id, reward_policy_id, is_completed, created_at)
				values (?, ?, ?, ?, true, ?)
				""", bytes(submissionId), bytes(missionId), bytes(userId), bytes(policyId), SEED_TIME);
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
				insert into game_plays (id, game_id, user_id, rule_version, created_at)
				values (?, ?, ?, 'v1', ?)
				""", bytes(playId), bytes(gameId), bytes(userId), SEED_TIME);
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

	/**
	 * 이벤트를 직접 넣는다. 시각은 UTC 원값이며, 정리 때 지워지도록 추적한다.
	 *
	 * @param eventType {@code NO_TICKET} 또는 {@code TICKET}
	 * @param maxTicketsPerUser 사용자별 누적 상한. 상한 없음(월말 소진용)이면 null
	 * @param membershipRule {@code excellent}, {@code vip}, {@code vvip}
	 * @param status 이벤트 상태 이름
	 * @param deletedAt 삭제 시각. 삭제되지 않았으면 null
	 */
	public UUID event(String eventType, boolean weightingEnabled, Integer maxTicketsPerUser, String membershipRule,
			Instant startsAt, Instant endsAt, String status, Instant deletedAt) {
		UUID eventId = UUID.randomUUID();
		jdbc.update("""
				insert into events (id, title, description, event_type, weighting_enabled, max_tickets_per_user,
				  starts_at, ends_at, status, created_at, updated_at, deleted_at, membership_rule)
				values (?, '응모 테스트 이벤트', '설명', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", bytes(eventId), eventType, weightingEnabled, maxTicketsPerUser,
				LocalDateTime.ofInstant(startsAt, ZoneOffset.UTC), LocalDateTime.ofInstant(endsAt, ZoneOffset.UTC),
				status, SEED_TIME, SEED_TIME,
				deletedAt == null ? null : LocalDateTime.ofInstant(deletedAt, ZoneOffset.UTC), membershipRule);
		events.add(eventId);
		return eventId;
	}

	/** 사용자에게 응모권 한 장을 직접 넣는다(사용 가능, 버전 1). 만료 시각은 UTC 원값이다. */
	public UUID ticket(UUID userId, String grade, Instant expiresAt) {
		MissionParents parents = missionParents(userId);
		UUID claimId = missionClaim(userId, parents, 1);
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into tickets (id, user_id, mission_reward_claim_id, grade, status, expires_at, version,
				  created_at, updated_at)
				values (?, ?, ?, ?, 'AVAILABLE', ?, 1, ?, ?)
				""", bytes(id), bytes(userId), bytes(claimId), grade,
				LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC), SEED_TIME.minusDays(1), SEED_TIME.minusDays(1));
		return id;
	}

	/**
	 * 사용 이력의 FK가 가리킬 응모 행. 새 이벤트와 참여자를 함께 만든다. 응모 ID는 운영처럼 시간에 가깝게 정렬되는 UUID v7이다(같은 밀리초 안의 순서는 보장하지 않는다).
	 * 호출자 트랜잭션 안팎 어디서든 부를 수 있다.
	 */
	public UUID eventEntry(UUID userId) {
		UUID eventId = UUID.randomUUID();
		jdbc.update("""
				insert into events (id, title, description, event_type, weighting_enabled, starts_at, ends_at,
				  status, created_at, updated_at, membership_rule)
				values (?, '테스트 이벤트', '설명', 'TICKET', false, ?, ?, 'OPEN', ?, ?, 'excellent')
				""", bytes(eventId), SEED_TIME, SEED_TIME.plusMonths(3), SEED_TIME, SEED_TIME);
		events.add(eventId);
		UUID participantId = UUID.randomUUID();
		jdbc.update("""
				insert into event_participants (id, event_id, user_id, created_at, used_ticket_count)
				values (?, ?, ?, ?, 0)
				""", bytes(participantId), bytes(eventId), bytes(userId), SEED_TIME);
		UUID entryId = uuidV7();
		jdbc.update("""
				insert into event_entries (id, participant_id, user_id, requested_ticket_count,
				  deducted_ticket_count, created_at)
				values (?, ?, ?, 0, 0, ?)
				""", bytes(entryId), bytes(participantId), bytes(userId), SEED_TIME);
		return entryId;
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

	/**
	 * 운영의 응모 ID처럼 시간순으로 커지는 UUID v7을 만든다. 서로 다른 응모의 키가 인덱스 끝에 몰리는 분포를 재현한다.
	 */
	public static UUID uuidV7() {
		long millis = System.currentTimeMillis();
		java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
		long most = (millis << 16) | 0x7000L | (random.nextLong() & 0x0FFFL);
		long least = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
		return new UUID(most, least);
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
