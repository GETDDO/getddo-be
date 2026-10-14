package com.getddo.core.attendance.domain;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StreakMilestoneTest {

	@ParameterizedTest
	@ValueSource(ints = {1, 28})
	@DisplayName("단계 일수가 1~28 경계값이면 만든다")
	void acceptsBoundaryDays(int milestoneDays) {
		// given
		// when
		StreakMilestone milestone = new StreakMilestone(UUID.randomUUID(), milestoneDays, 1);
		// then
		assertThat(milestone.getMilestoneDays()).isEqualTo(milestoneDays);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 29})
	@DisplayName("단계 일수가 1~28 밖이면 거절한다")
	void rejectsDaysOutOfRange(int milestoneDays) {
		// given
		// when
		// then
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new StreakMilestone(UUID.randomUUID(), milestoneDays, 1));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1})
	@DisplayName("지급 수량이 1 미만이면 거절한다")
	void rejectsNonPositiveCount(int rewardTicketCount) {
		// given
		// when
		// then
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new StreakMilestone(UUID.randomUUID(), 7, rewardTicketCount));
	}
}
