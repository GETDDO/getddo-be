package com.getddo.api.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationResponseTest {
	@Test
	@DisplayName("알림 목록과 읽음 응답의 필수 ID가 null이면 생성 시 거절한다")
	void rejectsMissingIdentifier() {
		// given / when / then
		assertThatThrownBy(() -> new NotificationResponse(null, "제목", "내용", Instant.EPOCH,
				false, null, null)).isInstanceOf(NullPointerException.class).hasMessage("id");
		assertThatThrownBy(() -> new NotificationReadResponse(null, true))
				.isInstanceOf(NullPointerException.class).hasMessage("id");
	}

	@Test
	@DisplayName("관련 이벤트가 없는 응답도 필수 ID와 읽음 상태를 보존한다")
	void allowsAbsentRelatedEvent() {
		// given
		UUID id = UUID.randomUUID();

		// when
		NotificationResponse response = new NotificationResponse(id, "제목", "내용", Instant.EPOCH,
				false, null, null);
		NotificationReadResponse readResponse = new NotificationReadResponse(id, true);

		// then
		assertThat(response.getId()).isEqualTo(id);
		assertThat(response.getEventId()).isNull();
		assertThat(readResponse.getId()).isEqualTo(id);
		assertThat(readResponse.isRead()).isTrue();
	}
}
