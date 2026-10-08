package com.getddo.core.ticket.service;

import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketGrade;

import static org.assertj.core.api.Assertions.assertThat;

class RandomTicketGradeDrawerTest {

	@ParameterizedTest
	@CsvSource({"0,BRONZE", "79,BRONZE", "80,SILVER", "97,SILVER", "98,GOLD", "99,GOLD"})
	@DisplayName("미션·게임은 0~99 중 80 미만 브론즈, 98 미만 실버, 나머지 골드다")
	void drawsByThreshold(int roll, TicketGrade expected) {
		// given
		RandomTicketGradeDrawer drawer = new RandomTicketGradeDrawer(new FixedRoll(roll));
		// when
		// then
		assertThat(drawer.draw(GrantSourceType.MISSION)).isEqualTo(expected);
		assertThat(drawer.draw(GrantSourceType.GAME)).isEqualTo(expected);
	}

	@Test
	@DisplayName("출석은 난수와 관계없이 브론즈로 고정한다")
	void attendanceIsAlwaysBronze() {
		// given
		RandomTicketGradeDrawer drawer = new RandomTicketGradeDrawer(new FixedRoll(99));
		// when
		TicketGrade grade = drawer.draw(GrantSourceType.ATTENDANCE);
		// then
		assertThat(grade).isEqualTo(TicketGrade.BRONZE);
	}

	@Test
	@DisplayName("난수 범위는 0 이상 100 미만이다")
	void rollsWithinHundred() {
		// given
		FixedRoll random = new FixedRoll(0);
		RandomTicketGradeDrawer drawer = new RandomTicketGradeDrawer(random);
		// when
		drawer.draw(GrantSourceType.MISSION);
		// then
		assertThat(random.lastBound).isEqualTo(100);
	}

	private static final class FixedRoll implements RandomGenerator {

		private final int roll;
		private int lastBound;

		FixedRoll(int roll) {
			this.roll = roll;
		}

		@Override
		public int nextInt(int bound) {
			lastBound = bound;
			return roll;
		}

		@Override
		public long nextLong() {
			throw new UnsupportedOperationException();
		}
	}
}
