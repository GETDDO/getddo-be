package com.getddo.db.attendance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.getddo.core.attendance.domain.AttendanceReceipt;
import com.getddo.core.attendance.domain.AttendanceRewardReceipt;
import com.getddo.core.attendance.domain.AttendanceRewardType;
import com.getddo.core.attendance.exception.AttendanceErrorCode;
import com.getddo.core.attendance.exception.AttendanceException;
import com.getddo.core.attendance.service.AttendanceService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** GD-47 테스트 방법(중복 요청, KST 자정 경계, 단계 보상, 월초 초기화)을 실제 MySQL에서 검증한다. */
class AttendanceServiceIntegrationTest extends AttendanceIntegrationTestSupport {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Autowired
	private AttendanceService attendanceService;

	/** 일일 1장, 7·14·28일 단계 1·3·7장 정책을 2026년 전체에 적용한다. */
	private void seedDefaultPolicies() {
		policies.dailyPolicy(adminId, 1, Instant.parse("2025-12-31T15:00:00Z"), null);
		policies.streakPolicySet(adminId, LocalDate.parse("2026-01-01"), AttendanceSeeds.DEFAULT_MILESTONES);
	}

	/** KST 날짜·시각에 출석한다. */
	private AttendanceReceipt attendAt(String kstDateTime) {
		clock.set(LocalDateTime.parse(kstDateTime).atZone(KST).toInstant());
		return attendanceService.attend(userId);
	}

	private AttendanceReceipt attendOn(LocalDate kstDate) {
		return attendAt(kstDate + "T12:00:00");
	}

	/** 날짜 범위의 매일 정오(KST)에 출석하고 마지막 영수증을 돌려준다. */
	private AttendanceReceipt attendEveryDay(LocalDate from, LocalDate toInclusive) {
		AttendanceReceipt last = null;
		for (LocalDate day = from; !day.isAfter(toInclusive); day = day.plusDays(1)) {
			last = attendOn(day);
		}
		return last;
	}

	private long streakClaims(String sourceKey) {
		return count("""
				select count(*) from attendance_reward_claims
				where user_id = ? and reward_type = 'STREAK' and source_key = ?
				""", bytes(userId), sourceKey);
	}

	/** 출석 청구마다 청구 수량만큼 응모권과 지급 이력이 있고, 응모권 합계가 청구 수량 합계와 같은지 확인한다. */
	private void assertClaimsAndTicketsConsistent() {
		long claimTickets = count("select coalesce(sum(ticket_count), 0) from attendance_reward_claims where user_id = ?",
				bytes(userId));
		long tickets = count("select count(*) from tickets where user_id = ?", bytes(userId));
		long histories = count("""
				select count(*) from ticket_histories h join tickets t on t.id = h.ticket_id where t.user_id = ?
				""", bytes(userId));
		long claimsWithWrongTicketCount = count("""
				select count(*) from attendance_reward_claims c
				where c.user_id = ?
				  and c.ticket_count <> (select count(*) from tickets t where t.attendance_reward_claim_id = c.id)
				""", bytes(userId));
		assertThat(claimsWithWrongTicketCount).isZero();
		assertThat(tickets).isEqualTo(claimTickets).isEqualTo(histories);
	}

	@Test
	@DisplayName("새 출석은 출석·연속 현황·일일 청구·응모권 지급을 함께 기록한다")
	void newAttendanceRecordsEverything() {
		// given
		seedDefaultPolicies();
		// when
		AttendanceReceipt receipt = attendAt("2026-09-15T12:00:00");
		// then
		assertThat(receipt.isCreated()).isTrue();
		assertThat(receipt.getAttendanceDate()).isEqualTo(LocalDate.parse("2026-09-15"));
		assertThat(receipt.getConsecutiveDays()).isEqualTo(1);
		assertThat(receipt.getRewards()).singleElement().satisfies(reward -> {
			assertThat(reward.getRewardType()).isEqualTo(AttendanceRewardType.DAILY);
			assertThat(reward.getTicketCount()).isEqualTo(1);
			assertThat(reward.getExpiresAt()).isEqualTo(Instant.parse("2026-09-30T15:00:00Z"));
		});
		UUID claimId = receipt.getRewards().get(0).getClaimId();
		assertThat(count("select count(*) from attendances where user_id = ?", bytes(userId))).isEqualTo(1);
		assertThat(count("select count(*) from attendance_reward_claims where id = ? and source_key = '2026-09-15'",
				bytes(claimId))).isEqualTo(1);
		assertThat(count("select count(*) from tickets where attendance_reward_claim_id = ?", bytes(claimId)))
				.isEqualTo(1);
		assertThat(count("select consecutive_days from attendance_streaks where user_id = ?", bytes(userId)))
				.isEqualTo(1);
		assertClaimsAndTicketsConsistent();
	}

	@Test
	@DisplayName("같은 날 다시 요청하면 새로 기록·지급하지 않고 처음과 같은 출석·보상을 created=false로 돌려준다")
	void sameDayRepeatReturnsExisting() {
		// given
		seedDefaultPolicies();
		AttendanceReceipt first = attendAt("2026-09-15T09:00:00");
		// when
		AttendanceReceipt repeated = attendAt("2026-09-15T21:00:00");
		// then
		assertThat(repeated.isCreated()).isFalse();
		assertThat(repeated.getAttendanceId()).isEqualTo(first.getAttendanceId());
		assertThat(repeated.getConsecutiveDays()).isEqualTo(first.getConsecutiveDays());
		assertThat(repeated.getRewards()).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(first.getRewards());
		assertThat(repeated.getCreatedAt()).isEqualTo(first.getCreatedAt());
		assertThat(count("select count(*) from attendances where user_id = ?", bytes(userId))).isEqualTo(1);
		assertClaimsAndTicketsConsistent();
		assertThat(count("select count(*) from tickets where user_id = ?", bytes(userId))).isEqualTo(1);
	}

	@Test
	@DisplayName("시계가 나노초를 주어도 첫 응답과 같은 날 재요청의 출석 기록 시각이 DB 저장 값으로 같다")
	void createdAtMatchesStoredValue() {
		// given
		seedDefaultPolicies();
		clock.set(Instant.parse("2026-09-15T03:00:00.123456789Z"));
		// when
		AttendanceReceipt first = attendanceService.attend(userId);
		AttendanceReceipt repeated = attendanceService.attend(userId);
		// then
		Instant stored = jdbc.queryForObject("select created_at from attendances where id = ?",
				LocalDateTime.class, bytes(first.getAttendanceId())).toInstant(ZoneOffset.UTC);
		assertThat(first.getCreatedAt()).isEqualTo(stored);
		assertThat(repeated.getCreatedAt()).isEqualTo(stored);
	}

	@Test
	@DisplayName("UTC로는 같은 9/30이어도 KST 자정을 넘으면 다른 출석이며, 10/1은 새 달이라 연속 1일부터 센다")
	void kstMidnightBoundary() {
		// given
		seedDefaultPolicies();
		// when: 2026-09-30T14:59:59Z(KST 9/30 23:59:59), 2026-09-30T15:00:00Z(KST 10/1 00:00)
		AttendanceReceipt beforeMidnight = attendAt("2026-09-30T23:59:59");
		AttendanceReceipt afterMidnight = attendAt("2026-10-01T00:00:00");
		// then
		assertThat(beforeMidnight.getAttendanceDate()).isEqualTo(LocalDate.parse("2026-09-30"));
		assertThat(afterMidnight.getAttendanceDate()).isEqualTo(LocalDate.parse("2026-10-01"));
		assertThat(afterMidnight.isCreated()).isTrue();
		assertThat(afterMidnight.getConsecutiveDays()).isEqualTo(1);
		assertThat(count("select count(*) from attendances where user_id = ?", bytes(userId))).isEqualTo(2);
		assertThat(count("select count(*) from attendance_streaks where user_id = ?", bytes(userId))).isEqualTo(2);
		assertClaimsAndTicketsConsistent();
	}

	@Test
	@DisplayName("7·14·28일 연속에 도달한 날 각 단계 보상을 따로 지급해 9월 최대 보상 11장을 받는다")
	void paysEachMilestone() {
		// given
		seedDefaultPolicies();
		// when
		AttendanceReceipt day7 = attendEveryDay(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-07"));
		AttendanceReceipt day14 = attendEveryDay(LocalDate.parse("2026-09-08"), LocalDate.parse("2026-09-14"));
		AttendanceReceipt day28 = attendEveryDay(LocalDate.parse("2026-09-15"), LocalDate.parse("2026-09-28"));
		// then
		assertThat(day7.getRewards()).extracting(AttendanceRewardReceipt::getMilestoneDays).containsExactly(null, 7);
		assertThat(day14.getRewards()).extracting(AttendanceRewardReceipt::getTicketCount).containsExactly(1, 3);
		assertThat(day28.getRewards()).extracting(AttendanceRewardReceipt::getTicketCount).containsExactly(1, 7);
		assertThat(day28.getConsecutiveDays()).isEqualTo(28);
		assertThat(count("""
				select coalesce(sum(ticket_count), 0) from attendance_reward_claims
				where user_id = ? and reward_type = 'STREAK'
				""", bytes(userId))).isEqualTo(11);
		assertClaimsAndTicketsConsistent();
		assertThat(count("select count(*) from tickets where user_id = ?", bytes(userId))).isEqualTo(28 + 11);
	}

	@Test
	@DisplayName("연속이 끊긴 뒤 같은 달에 다시 7일이 되어도 7일 단계 보상을 다시 지급하지 않는다")
	void doesNotRepayMilestoneAfterBreak() {
		// given: 9/1~9/7 연속으로 7일 보상을 받고 9/8 결석
		seedDefaultPolicies();
		attendEveryDay(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-07"));
		// when: 9/9~9/15 다시 7일 연속
		AttendanceReceipt again = attendEveryDay(LocalDate.parse("2026-09-09"), LocalDate.parse("2026-09-15"));
		// then
		assertThat(again.getConsecutiveDays()).isEqualTo(7);
		assertThat(again.getRewards()).extracting(AttendanceRewardReceipt::getRewardType)
				.containsExactly(AttendanceRewardType.DAILY);
		assertThat(streakClaims("2026-09:7")).isEqualTo(1);
		assertClaimsAndTicketsConsistent();
	}

	@Test
	@DisplayName("월초에는 연속 일수가 초기화되어 9월 말 7일 연속 뒤 10/1은 1일이다")
	void resetsAtMonthStart() {
		// given
		seedDefaultPolicies();
		AttendanceReceipt septemberEnd = attendEveryDay(LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-30"));
		// when
		AttendanceReceipt octoberFirst = attendOn(LocalDate.parse("2026-10-01"));
		// then
		assertThat(septemberEnd.getConsecutiveDays()).isEqualTo(7);
		assertThat(octoberFirst.getConsecutiveDays()).isEqualTo(1);
		assertThat(streakClaims("2026-09:7")).isEqualTo(1);
		assertThat(streakClaims("2026-10:7")).isZero();
	}

	@Test
	@DisplayName("31일 연속 출석하면 실제 연속 일수 31을 돌려준다")
	void countsThirtyOneDays() {
		// given
		seedDefaultPolicies();
		// when
		AttendanceReceipt last = attendEveryDay(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
		// then
		assertThat(last.getConsecutiveDays()).isEqualTo(31);
		assertThat(count("select consecutive_days from attendance_streaks where user_id = ?", bytes(userId)))
				.isEqualTo(31);
		assertClaimsAndTicketsConsistent();
	}

	@Test
	@DisplayName("일일 정책이 없으면 ATTENDANCE-001로 실패하고 아무 행도 남기지 않는다")
	void failsWithoutDailyPolicy() {
		// given: 정책을 넣지 않음
		// when
		// then
		assertThatThrownBy(() -> attendAt("2026-09-15T12:00:00"))
				.isInstanceOf(AttendanceException.class)
				.extracting(error -> ((AttendanceException) error).getErrorCode())
				.isEqualTo(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND);
		assertThat(count("select count(*) from attendances where user_id = ?", bytes(userId))).isZero();
	}

	@Test
	@DisplayName("연속 출석 정책이 없으면 이미 저장한 출석까지 롤백되어 출석·청구·응모권이 남지 않는다")
	void rollsBackWithoutStreakPolicy() {
		// given: 일일 정책만 있음
		policies.dailyPolicy(adminId, 1, Instant.parse("2025-12-31T15:00:00Z"), null);
		// when
		// then
		assertThatThrownBy(() -> attendAt("2026-09-15T12:00:00"))
				.isInstanceOf(AttendanceException.class)
				.extracting(error -> ((AttendanceException) error).getErrorCode())
				.isEqualTo(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND);
		assertThat(count("select count(*) from attendances where user_id = ?", bytes(userId))).isZero();
		assertThat(count("select count(*) from attendance_reward_claims where user_id = ?", bytes(userId))).isZero();
		assertThat(count("select count(*) from tickets where user_id = ?", bytes(userId))).isZero();
	}
}
