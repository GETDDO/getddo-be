package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.drawing.domain.AwardCancellation;
import com.getddo.db.drawing.mapper.AwardCancellationMapper;

import static org.assertj.core.api.Assertions.*;

class AwardCancellationEntityTest {
	@Test
	@DisplayName("기존 미연결 취소 행을 업무 판단 없이 그대로 복원한다")
	void mapsUnlinkedStoredHistory() {
		// given
		AwardCancellation history = new AwardCancellation(UUID.randomUUID(), UUID.randomUUID(), null,
				UUID.randomUUID(), "과거 사유", Instant.parse("2026-10-12T00:00:00Z"));
		AwardCancellationEntity legacy = new AwardCancellationEntity(history);
		// when / then
		assertThat(new AwardCancellationMapper().toDomain(legacy)).isEqualTo(history);
	}

	@Test
	@DisplayName("어뷰징 근거 없이 사유·처리자·연결 실행을 변환한다")
	void mapsReasonActorAndConnectedRunWithoutAbuseEvidence() {
		AwardCancellation cancellation = new AwardCancellation(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), "관리자 사유", Instant.parse("2026-10-12T00:00:00Z"));
		AwardCancellationEntity entity = new AwardCancellationEntity(cancellation);
		assertThat(new AwardCancellationMapper().toDomain(entity)).isEqualTo(cancellation);
	}

	@Test
	@DisplayName("취소 사유가 없으면 거절한다")
	void rejectsMissingReason() {
		assertThatThrownBy(() -> new AwardCancellation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), "  ", Instant.parse("2026-10-12T00:00:00Z"))).isInstanceOf(IllegalArgumentException.class);
	}
}
