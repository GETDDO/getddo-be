package com.getddo.db.notification.mapper;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import com.getddo.core.notification.domain.Notification;
import com.getddo.db.notification.entity.NotificationEntity;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMapperTest {
	private final NotificationMapper mapper = Mappers.getMapper(NotificationMapper.class);

	/** 읽음·미읽음과 nullable 이벤트 정보를 포함한 모든 공개 필드의 변환을 확인한다. */
	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("MapStruct 변환은 읽음 여부와 관련 이벤트가 없는 경우도 보존한다")
	void preservesNotificationFields(boolean isRead) throws Exception {
		// given
		UUID id = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-10-11T00:00:00Z");
		var constructor = NotificationEntity.class.getDeclaredConstructor();
		constructor.setAccessible(true);
		NotificationEntity entity = constructor.newInstance();
		ReflectionTestUtils.setField(entity, "id", id);
		ReflectionTestUtils.setField(entity, "title", "제목");
		ReflectionTestUtils.setField(entity, "body", "내용");
		ReflectionTestUtils.setField(entity, "createdAt", createdAt);
		ReflectionTestUtils.setField(entity, "isRead", isRead);
		ReflectionTestUtils.setField(entity, "eventId", eventId);
		ReflectionTestUtils.setField(entity, "linkUrl", "/events/" + eventId);

		// when / then
		assertThat(mapper.toDomain(entity)).isEqualTo(new Notification(id, "제목", "내용", createdAt,
				isRead, eventId, "/events/" + eventId));
		ReflectionTestUtils.setField(entity, "eventId", null);
		ReflectionTestUtils.setField(entity, "linkUrl", null);
		assertThat(mapper.toDomain(entity)).isEqualTo(new Notification(id, "제목", "내용", createdAt,
				isRead, null, null));
	}
}
