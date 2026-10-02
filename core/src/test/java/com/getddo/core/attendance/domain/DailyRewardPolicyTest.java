package com.getddo.core.attendance.domain;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DailyRewardPolicyTest {

	@Test
	@DisplayName("지급 수량이 1이면 만든다")
	void acceptsMinimumCount() {
		// given
		// when
		DailyRewardPolicy policy = new DailyRewardPolicy(UUID.randomUUID(), 1);
		// then
		assertThat(policy.getRewardTicketCount()).isEqualTo(1);
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1})
	@DisplayName("지급 수량이 1 미만이면 거절한다")
	void rejectsNonPositiveCount(int rewardTicketCount) {
		// given
		// when
		// then
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new DailyRewardPolicy(UUID.randomUUID(), rewardTicketCount));
	}
}
