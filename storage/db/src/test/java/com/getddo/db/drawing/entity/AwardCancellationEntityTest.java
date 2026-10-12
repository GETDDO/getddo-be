package com.getddo.db.drawing.entity;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.drawing.domain.AwardCancellation;
import com.getddo.db.drawing.mapper.AwardCancellationMapper;
import com.getddo.core.common.exception.BusinessException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwardCancellationEntityTest {
	@Test
	@DisplayName("기존 미연결 취소 행은 NPE 대신 상태 충돌로 거절한다")
	void rejectsUnlinkedStoredHistory() {
		// given
		AwardCancellationEntity legacy = mock(AwardCancellationEntity.class);
		// when / then
		assertThatThrownBy(() -> new AwardCancellationMapper().toDomain(legacy)).isInstanceOfSatisfying(BusinessException.class,
				exception -> assertThat(exception.getErrorCode()).isEqualTo(com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN));
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
	@DisplayName("사유 또는 등록된 실행 연결이 없으면 거절한다")
	void rejectsMissingReasonOrRunConnection() {
		assertThatThrownBy(() -> new AwardCancellation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), "  ", Instant.parse("2026-10-12T00:00:00Z"))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new AwardCancellation(UUID.randomUUID(), UUID.randomUUID(), null,
				UUID.randomUUID(), "사유", Instant.parse("2026-10-12T00:00:00Z"))).isInstanceOf(NullPointerException.class);
	}
}
