package com.getddo.db.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static com.getddo.db.ticket.TicketGrantSeeds.uuid;

/**
 * 출석 통합 테스트용 정책 시드.
 *
 * <p>정책 데이터는 운영 DB에 넣지 않고 테스트가 임시 MySQL에 직접 넣는다. 등록자는 {@code TicketGrantSeeds}로 만든
 * 사용자이며, 테스트가 끝나면 {@code TicketGrantSeeds.cleanUp()}이 등록자 기준으로 함께 지운다.</p>
 */
public class AttendanceSeeds {

	private static final LocalDateTime SEED_TIME = LocalDateTime.of(2026, 7, 1, 0, 0);
	/** 초기 설정: 7일·14일·28일 단계에 1장·3장·7장. */
	public static final Map<Integer, Integer> DEFAULT_MILESTONES = defaultMilestones();

	private final JdbcTemplate jdbc;

	public AttendanceSeeds(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * 일일 출석 보상 정책을 넣는다. 종료 시각이 null이면 종료 없는 정책이다.
	 * 종료 없는 ATTENDANCE 정책은 DB에 하나만 둘 수 있다({@code open_guard}).
	 */
	public UUID dailyPolicy(UUID createdBy, int ticketCount, Instant effectiveFrom, Instant effectiveUntil) {
		UUID id = UUID.randomUUID();
		jdbc.update("""
				insert into reward_policies (id, created_by, reward_type, game_id, reward_ticket_count,
				  effective_from, effective_until, created_at)
				values (?, ?, 'ATTENDANCE', null, ?, ?, ?, ?)
				""", bytes(id), bytes(createdBy), ticketCount, utc(effectiveFrom),
				effectiveUntil == null ? null : utc(effectiveUntil), SEED_TIME);
		return id;
	}

	/**
	 * 연속 출석 정책 묶음과 단계를 넣는다. 적용월은 DB 전체에서 하나만 둘 수 있다.
	 *
	 * @param milestones 단계 일수 → 지급 수량
	 * @return 묶음 ID
	 */
	public UUID streakPolicySet(UUID createdBy, LocalDate effectiveMonth, Map<Integer, Integer> milestones) {
		UUID setId = UUID.randomUUID();
		jdbc.update("""
				insert into attendance_streak_policy_sets (id, created_by, effective_month, created_at)
				values (?, ?, ?, ?)
				""", bytes(setId), bytes(createdBy), effectiveMonth, SEED_TIME);
		milestones.forEach((days, count) -> jdbc.update("""
				insert into attendance_streak_policies (id, policy_set_id, milestone_days, reward_ticket_count,
				  created_at)
				values (?, ?, ?, ?, ?)
				""", bytes(UUID.randomUUID()), bytes(setId), days, count, SEED_TIME));
		return setId;
	}

	/** 단계 정책 ID를 조회한다. */
	public UUID milestoneId(UUID policySetId, int milestoneDays) {
		byte[] id = jdbc.queryForObject(
				"select id from attendance_streak_policies where policy_set_id = ? and milestone_days = ?",
				byte[].class, bytes(policySetId), milestoneDays);
		return uuid(id);
	}

	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static Map<Integer, Integer> defaultMilestones() {
		Map<Integer, Integer> milestones = new LinkedHashMap<>();
		milestones.put(7, 1);
		milestones.put(14, 3);
		milestones.put(28, 7);
		return Map.copyOf(milestones);
	}
}
