package com.getddo.core.attendance.domain;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendanceMilestoneStatusTest {

	private static final Instant CLAIMED_AT = Instant.parse("2026-10-07T03:00:00Z");

	@Test
	@DisplayName("단계 일수는 1~28, 수량은 1 이상일 때만 만들 수 있다")
	void rejectsOutOfRangeValues() {
		// given
		// when
		// then
		assertThatThrownBy(() -> new AttendanceMilestoneStatus(0, 1, false, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AttendanceMilestoneStatus(29, 1, false, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AttendanceMilestoneStatus(7, 0, false, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(new AttendanceMilestoneStatus(1, 1, false, null).getMilestoneDays()).isEqualTo(1);
		assertThat(new AttendanceMilestoneStatus(28, 7, false, null).getMilestoneDays()).isEqualTo(28);
	}

	@Test
	@DisplayName("받지 않은 단계에는 수령 시각이 있을 수 없고, 받았는데 시각이 없는 경우는 허용한다")
	void claimedAtConsistency() {
		// given
		// when
		// then
		assertThatThrownBy(() -> new AttendanceMilestoneStatus(7, 1, false, CLAIMED_AT))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(new AttendanceMilestoneStatus(7, 1, true, null).getClaimedAt()).isNull();
		assertThat(new AttendanceMilestoneStatus(7, 1, true, CLAIMED_AT).getClaimedAt()).isEqualTo(CLAIMED_AT);
	}
}
