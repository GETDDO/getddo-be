package com.getddo.core.attendance.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreakPolicySetTest {

	private static final StreakPolicySet SET = new StreakPolicySet(UUID.randomUUID(), List.of(
			new StreakMilestone(UUID.randomUUID(), 28, 7),
			new StreakMilestone(UUID.randomUUID(), 7, 1),
			new StreakMilestone(UUID.randomUUID(), 14, 3)));

	private static List<LocalDate> september(int... days) {
		List<LocalDate> dates = new ArrayList<>();
		for (int day : days) {
			dates.add(LocalDate.of(2026, 9, day));
		}
		return dates;
	}

	private static List<LocalDate> september(int from, int toInclusive) {
		List<LocalDate> dates = new ArrayList<>();
		for (int day = from; day <= toInclusive; day++) {
			dates.add(LocalDate.of(2026, 9, day));
		}
		return dates;
	}

	@Test
	@DisplayName("단계는 단계 일수 오름차순으로 정렬된다")
	void sortsMilestones() {
		// given
		// when
		// then
		assertThat(SET.getMilestones()).extracting(StreakMilestone::getMilestoneDays).containsExactly(7, 14, 28);
	}

	@Test
	@DisplayName("단계 일수째 되는 날이 도달일이고 도달하지 못한 단계는 돌려주지 않는다")
	void reachedOnExactDay() {
		// given
		// when
		List<ReachedMilestone> reached = SET.reachedBy(september(1, 15));
		// then
		assertThat(reached).extracting(r -> r.getMilestone().getMilestoneDays(), ReachedMilestone::getReachedDate)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(7, LocalDate.parse("2026-09-07")),
						org.assertj.core.groups.Tuple.tuple(14, LocalDate.parse("2026-09-14")));
		assertThat(SET.reachedBy(september(1, 6))).isEmpty();
		assertThat(SET.reachedBy(List.of())).isEmpty();
	}

	@Test
	@DisplayName("연속이 끊긴 뒤 다시 도달해도 처음 도달한 날짜만 돌려준다")
	void firstReachOnly() {
		// given: 1~7일 도달 후 하루 빠지고 9~15일 다시 7일 연속
		List<LocalDate> dates = new ArrayList<>(september(1, 7));
		dates.addAll(september(9, 15));
		// when
		List<ReachedMilestone> reached = SET.reachedBy(dates);
		// then
		assertThat(reached).singleElement().satisfies(r -> {
			assertThat(r.getMilestone().getMilestoneDays()).isEqualTo(7);
			assertThat(r.getReachedDate()).isEqualTo(LocalDate.parse("2026-09-07"));
		});
	}

	@Test
	@DisplayName("늦게 저장된 중간 날짜가 앞뒤 구간을 이으면 그 순간 단계에 도달한다")
	void lateMiddleDayCompletesRun() {
		// given
		List<LocalDate> withoutSeventh = september(1, 2, 3, 4, 5, 6, 8);
		List<LocalDate> withSeventh = september(1, 8);
		// when
		// then
		assertThat(SET.reachedBy(withoutSeventh)).isEmpty();
		assertThat(SET.reachedBy(withSeventh)).singleElement()
				.satisfies(r -> assertThat(r.getReachedDate()).isEqualTo(LocalDate.parse("2026-09-07")));
	}
}
