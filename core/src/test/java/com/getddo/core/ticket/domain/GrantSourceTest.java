package com.getddo.core.ticket.domain;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GrantSourceTest {

	private static final UUID CLAIM_ID = UUID.fromString("0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b");

	@Test
	@DisplayName("멱등키는 GRANT:{종류}:{청구ID} 형식이다")
	void buildsIdempotencyKeyFromTypeAndClaimId() {
		// given
		GrantSource source = new GrantSource(GrantSourceType.MISSION, CLAIM_ID);
		// when
		String key = source.idempotencyKey();
		// then
		assertThat(key).isEqualTo("GRANT:MISSION:0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b");
	}

	@Test
	@DisplayName("같은 청구 ID라도 청구 종류가 다르면 멱등키가 다르다")
	void keysDifferByType() {
		// given
		GrantSource mission = new GrantSource(GrantSourceType.MISSION, CLAIM_ID);
		GrantSource attendance = new GrantSource(GrantSourceType.ATTENDANCE, CLAIM_ID);
		GrantSource game = new GrantSource(GrantSourceType.GAME, CLAIM_ID);
		// when
		// then
		assertThat(mission.idempotencyKey())
				.isNotEqualTo(attendance.idempotencyKey())
				.isNotEqualTo(game.idempotencyKey());
		assertThat(attendance.idempotencyKey()).isNotEqualTo(game.idempotencyKey());
	}

	@Test
	@DisplayName("모든 청구 종류의 멱등키가 원장 컬럼 길이 160자 안에 들어간다")
	void keyFitsLedgerColumn() {
		for (GrantSourceType type : GrantSourceType.values()) {
			// given
			GrantSource source = new GrantSource(type, CLAIM_ID);
			// when
			String key = source.idempotencyKey();
			// then
			assertThat(key).hasSizeLessThanOrEqualTo(160);
		}
	}
}
