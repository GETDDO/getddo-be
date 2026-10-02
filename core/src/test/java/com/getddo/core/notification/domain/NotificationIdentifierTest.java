package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationIdentifierTest {
	@Test
	@DisplayName("알림·작업·발송 입력의 필수 식별자가 null이면 생성 시 거절한다")
	void rejectsMissingRequiredIdentifiers() {
		// given
		UUID id = UUID.randomUUID();
		List<Runnable> constructors = List.of(
				() -> new Notification(null, "제목", "내용", Instant.EPOCH, false, null, null),
				() -> new NotificationJob(null, 1),
				() -> new NotificationDelivery(null, id, "제목", "내용", null, 1),
				() -> new NotificationDelivery(id, null, "제목", "내용", null, 1));

		// when / then
		for (Runnable constructor : constructors) {
			assertThatThrownBy(constructor::run).isInstanceOf(NullPointerException.class);
		}
	}

	@Test
	@DisplayName("관련 이벤트가 없는 알림은 필수 ID만으로 생성할 수 있다")
	void allowsAbsentRelatedEvent() {
		// given
		UUID id = UUID.randomUUID();

		// when
		Notification notification = new Notification(id, "제목", "내용", Instant.EPOCH, false, null, null);

		// then
		assertThat(notification.getId()).isEqualTo(id);
		assertThat(notification.getEventId()).isNull();
	}

	@Test
	@DisplayName("알림은 값 비교를 유지하고 문자열 출력에 알림 내용을 노출하지 않는다")
	void preservesValueEqualityWithoutPrintingContent() {
		// given
		UUID id = UUID.randomUUID();
		Notification notification = new Notification(id, "비공개 제목", "비공개 내용", Instant.EPOCH, false, null, null);
		Notification same = new Notification(id, "비공개 제목", "비공개 내용", Instant.EPOCH, false, null, null);
		Notification read = new Notification(id, "비공개 제목", "비공개 내용", Instant.EPOCH, true, null, null);

		// when / then
		assertThat(notification).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(read);
		NotificationDelivery delivery = new NotificationDelivery(id, id, "비공개 제목", "비공개 내용", null, 1);
		NotificationJobRequest request = new NotificationJobRequest("test", NotificationType.EVENT_START,
				null, null, "비공개 제목", "비공개 내용", null, Instant.EPOCH, List.of(id));
		for (Object model : List.of(notification, delivery, request, new NotificationJob(id, 1))) {
			assertThat(model.toString()).doesNotContain("비공개 제목", "비공개 내용", id.toString());
		}
	}
}
