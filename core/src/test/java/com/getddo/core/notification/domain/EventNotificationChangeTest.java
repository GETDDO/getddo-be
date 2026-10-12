package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getddo.core.notification.exception.NotificationProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventNotificationChangeTest {
	private static final Instant NOW = Instant.parse("2026-10-11T03:00:00Z");

	@ParameterizedTest
	@CsvSource({"300,299", "300,300", "0,600", "-1,600"})
	@DisplayName("앞당김·동일 일정·시작 이후 일정 변경은 거절한다")
	void rejectsForbiddenScheduleChange(long oldOffset, long newOffset) {
		// given / when / then
		assertThatThrownBy(() -> EventNotificationChange.scheduleChanged(UUID.randomUUID(), UUID.randomUUID(),
				"이벤트", NOW.plusSeconds(oldOffset), NOW.plusSeconds(newOffset), NOW))
				.isInstanceOf(NotificationProcessingException.class);
	}

	@Test
	@DisplayName("필수 식별자와 제목·발생 시각이 없으면 거절한다")
	void validatesRequiredFields() {
		// given / when / then
		assertThatThrownBy(() -> EventNotificationChange.canceled(null, UUID.randomUUID(), "이벤트", NOW))
				.isInstanceOf(NotificationProcessingException.class);
		assertThatThrownBy(() -> EventNotificationChange.canceled(UUID.randomUUID(), null, "이벤트", NOW))
				.isInstanceOf(NotificationProcessingException.class);
		assertThatThrownBy(() -> EventNotificationChange.canceled(UUID.randomUUID(), UUID.randomUUID(), " ", NOW))
				.isInstanceOf(NotificationProcessingException.class);
		assertThatThrownBy(() -> EventNotificationChange.canceled(UUID.randomUUID(), UUID.randomUUID(), "이벤트", null))
				.isInstanceOf(NotificationProcessingException.class);
	}

	@Test
	@DisplayName("발생 시각은 DB 마이크로초 정밀도로 정규화한다")
	void normalizesTimestamp() {
		// given / when
		EventNotificationChange change = EventNotificationChange.canceled(UUID.randomUUID(), UUID.randomUUID(),
				"이벤트", NOW.plusNanos(123456789));
		// then
		assertThat(change.getOccurredAt()).isEqualTo(NOW.plusNanos(123456000));
	}
}
