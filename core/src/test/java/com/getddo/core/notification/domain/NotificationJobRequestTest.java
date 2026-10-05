package com.getddo.core.notification.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.notification.exception.NotificationProcessingErrorCode;
import com.getddo.core.notification.exception.NotificationProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationJobRequestTest {
	private static final Instant NOW = Instant.parse("2026-10-01T00:00:00.123456789Z");

	/** 검증 시나리오: 수신자 순서·중복과 시각 정밀도 차이를 정규화하고 입력 목록 변경을 차단한다. */
	@Test
	@DisplayName("수신자 순서·중복과 시각 정밀도 차이를 정규화하고 입력 목록 변경을 차단한다")
	void normalizesAndCopiesRecipients() {
		// given
		UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
		UUID second = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
		List<UUID> recipients = new ArrayList<>(List.of(second, first, first));

		// when
		NotificationJobRequest request = request("발생-1", "제목", "내용", recipients);
		recipients.clear();

		// then
		assertThat(request.getRecipientIds()).containsExactly(first, second);
		assertThat(request.getScheduledAt()).isEqualTo(Instant.parse("2026-10-01T00:00:00.123456Z"));
		assertThatThrownBy(() -> request.getRecipientIds().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	/** 검증 시나리오: 필수 값·수신자 null·컬럼 길이 초과는 알림 작업 오류 코드로 거절한다. */
	@Test
	@DisplayName("필수 값·수신자 null·컬럼 길이 초과는 알림 작업 오류 코드로 거절한다")
	void rejectsInvalidInputs() {
		// given
		List<Runnable> invalid = List.of(
				() -> request(" ", "제목", "내용", List.of()),
				() -> request("x".repeat(161), "제목", "내용", List.of()),
				() -> request("발생", "제목".repeat(101), "내용", List.of()),
				() -> request("발생", "제목", " ", List.of()),
				() -> request("발생", "제목", "가".repeat(21_846), List.of()),
				() -> request("발생", "제목", "내용", Arrays.asList((UUID) null)));

		// when / then
		for (Runnable call : invalid) {
			assertThatThrownBy(call::run).isInstanceOf(NotificationProcessingException.class)
					.extracting(error -> ((NotificationProcessingException) error).getErrorCode())
					.isEqualTo(NotificationProcessingErrorCode.INVALID_JOB);
		}
	}

	/** 검증 시나리오: 보조 평면 문자는 UTF-16 길이가 아니라 DB의 문자 수로 제목 제한을 검증한다. */
	@Test
	@DisplayName("보조 평면 문자는 UTF-16 길이가 아니라 DB의 문자 수로 제목 제한을 검증한다")
	void countsUnicodeCharactersInsteadOfUtf16Units() {
		// given / when
		NotificationJobRequest request = request("발생", "😀".repeat(200), "내용", List.of());

		// then
		assertThat(request.getTitle().codePointCount(0, request.getTitle().length())).isEqualTo(200);
	}

	/** 각 시나리오에서 발생 키와 수신 대상을 지정할 유효한 알림 작업 입력을 만든다. */
	private static NotificationJobRequest request(String key, String title, String body, List<UUID> users) {
		return new NotificationJobRequest(key, NotificationType.RESULT_CHANGED,
				null, null, title, body, null, NOW, users);
	}
}
