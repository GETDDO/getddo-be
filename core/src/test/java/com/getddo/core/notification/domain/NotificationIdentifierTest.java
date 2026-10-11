package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationIdentifierTest {
	/** 검증 시나리오: 알림과 발송의 필수 문구 및 알림 생성 시각은 null을 거절한다. */
	@Test
	@DisplayName("알림과 발송의 필수 문구 및 알림 생성 시각은 null을 거절한다")
	void rejectsMissingRequiredContent() {
		// given
		UUID id = UUID.randomUUID();
		List<Runnable> constructors = List.of(
				() -> new Notification(id, null, "내용", Instant.EPOCH, false, null, null),
				() -> new Notification(id, "제목", null, Instant.EPOCH, false, null, null),
				() -> new Notification(id, "제목", "내용", null, false, null, null),
				() -> new NotificationDelivery(id, id, null, "내용", null, 0),
				() -> new NotificationDelivery(id, id, "제목", null, null, 0));

		// when / then
		for (Runnable constructor : constructors) {
			assertThatThrownBy(constructor::run).isInstanceOf(NullPointerException.class);
		}
	}

	/** 검증 시나리오: 작업과 발송의 음수 시도 횟수를 거절하고 0과 양수는 허용한다. */
	@Test
	@DisplayName("작업과 발송의 음수 시도 횟수를 거절하고 0과 양수는 허용한다")
	void rejectsNegativeAttemptsAndAllowsInitialCount() {
		// given
		UUID id = UUID.randomUUID();

		// when / then
		assertThatThrownBy(() -> new NotificationJob(id, -1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new NotificationDelivery(id, id, "제목", "내용", null, -1))
				.isInstanceOf(IllegalArgumentException.class);
		for (int attempt : List.of(0, 1, 4)) {
			assertThat(new NotificationJob(id, attempt).getAttemptCount()).isEqualTo(attempt);
			assertThat(new NotificationDelivery(id, id, "제목", "내용", null, attempt).getAttemptCount()).isEqualTo(attempt);
		}
	}

	/** 검증 시나리오: 알림·작업·발송 입력의 필수 식별자가 null이면 생성 시 거절한다. */
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

	/** 검증 시나리오: 관련 이벤트가 없는 알림은 필수 ID만으로 생성할 수 있다. */
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

	/** 알림의 모든 저장 필드와 nullable 필드의 동등성·해시 계약을 확인한다. */
	@Test
	@DisplayName("알림은 같은 값이면 해시가 같고 각 필드가 달라지면 비동등하다")
	void comparesAllNotificationFields() {
		// given
		UUID id = UUID.randomUUID();
		UUID event = UUID.randomUUID();
		Notification original = new Notification(id, "제목", "내용", Instant.EPOCH, false, event, "/events");
		Notification same = new Notification(id, "제목", "내용", Instant.EPOCH, false, event, "/events");
		Notification absent = new Notification(id, "제목", "내용", Instant.EPOCH, false, null, null);
		List<Notification> changed = List.of(
				new Notification(UUID.randomUUID(), "제목", "내용", Instant.EPOCH, false, event, "/events"),
				new Notification(id, "다른 제목", "내용", Instant.EPOCH, false, event, "/events"),
				new Notification(id, "제목", "다른 내용", Instant.EPOCH, false, event, "/events"),
				new Notification(id, "제목", "내용", Instant.EPOCH.plusSeconds(1), false, event, "/events"),
				new Notification(id, "제목", "내용", Instant.EPOCH, true, event, "/events"),
				new Notification(id, "제목", "내용", Instant.EPOCH, false, UUID.randomUUID(), "/events"),
				new Notification(id, "제목", "내용", Instant.EPOCH, false, null, "/events"),
				new Notification(id, "제목", "내용", Instant.EPOCH, false, event, "/other"),
				new Notification(id, "제목", "내용", Instant.EPOCH, false, event, null));

		// when / then
		assertThat(original).isEqualTo(original).isEqualTo(same).hasSameHashCodeAs(same)
				.isNotEqualTo(null).isNotEqualTo("제목");
		assertThat(same).isEqualTo(original);
		Notification absentCopy = new Notification(id, "제목", "내용", Instant.EPOCH, false, null, null);
		assertThat(absent).isEqualTo(absentCopy).hasSameHashCodeAs(absentCopy);
		for (Notification different : changed) {
			assertThat(original).isNotEqualTo(different);
			assertThat(different).isNotEqualTo(original);
		}
	}

	/** 발송 입력의 저장 필드, 선점 차수와 nullable 링크의 값 비교를 확인한다. */
	@Test
	@DisplayName("발송 입력은 같은 값이면 해시가 같고 각 필드와 차수가 달라지면 비동등하다")
	void comparesAllDeliveryFields() {
		// given
		UUID id = UUID.randomUUID();
		UUID user = UUID.randomUUID();
		NotificationDelivery original = new NotificationDelivery(id, user, "제목", "내용", "/events", 1);
		NotificationDelivery same = new NotificationDelivery(id, user, "제목", "내용", "/events", 1);
		NotificationDelivery absent = new NotificationDelivery(id, user, "제목", "내용", null, 1);
		List<NotificationDelivery> changed = List.of(
				new NotificationDelivery(UUID.randomUUID(), user, "제목", "내용", "/events", 1),
				new NotificationDelivery(id, UUID.randomUUID(), "제목", "내용", "/events", 1),
				new NotificationDelivery(id, user, "다른 제목", "내용", "/events", 1),
				new NotificationDelivery(id, user, "제목", "다른 내용", "/events", 1),
				new NotificationDelivery(id, user, "제목", "내용", "/other", 1),
				absent,
				new NotificationDelivery(id, user, "제목", "내용", "/events", 0));

		// when / then
		assertThat(original).isEqualTo(original).isEqualTo(same).hasSameHashCodeAs(same)
				.isNotEqualTo(null).isNotEqualTo("제목");
		assertThat(same).isEqualTo(original);
		NotificationDelivery absentCopy = new NotificationDelivery(id, user, "제목", "내용", null, 1);
		assertThat(absent).isEqualTo(absentCopy).hasSameHashCodeAs(absentCopy);
		for (NotificationDelivery different : changed) {
			assertThat(original).isNotEqualTo(different);
			assertThat(different).isNotEqualTo(original);
		}
	}

	/** 작업 ID와 선점 차수가 동등성에 모두 포함되는지 확인한다. */
	@Test
	@DisplayName("작업은 같은 ID와 차수이면 해시가 같고 ID 또는 차수 변경은 비동등하다")
	void comparesAllJobFields() {
		// given
		UUID id = UUID.randomUUID();
		NotificationJob original = new NotificationJob(id, 1);
		NotificationJob same = new NotificationJob(id, 1);
		List<NotificationJob> changed = List.of(new NotificationJob(UUID.randomUUID(), 1), new NotificationJob(id, 0));

		// when / then
		assertThat(original).isEqualTo(original).isEqualTo(same).hasSameHashCodeAs(same)
				.isNotEqualTo(null).isNotEqualTo(id);
		assertThat(same).isEqualTo(original);
		for (NotificationJob different : changed) {
			assertThat(original).isNotEqualTo(different);
			assertThat(different).isNotEqualTo(original);
		}
	}

	/** 검증 시나리오: 알림은 값 비교를 유지하고 문자열 출력에 알림 내용을 노출하지 않는다. */
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
