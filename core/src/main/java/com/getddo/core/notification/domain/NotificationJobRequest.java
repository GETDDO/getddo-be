package com.getddo.core.notification.domain;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.getddo.core.notification.exception.NotificationProcessingErrorCode;
import com.getddo.core.notification.exception.NotificationProcessingException;

/**
 * 업무 담당자가 확정한 발생 사실과 수신 대상을 전달하는 내부 작업 등록 입력이다.
 *
 * <p>발생 키는 같은 발생 건의 재요청에 재사용하고, 새 발생 건에는 새 키를 사용한다.
 * 수신 자격은 호출자가 판정한다. 예약된 시작 알림의 자격도 실제 생성 시점에 판정해야 하므로
 * 이벤트 등록 시점의 사용자 목록을 미리 확정하는 용도로 사용하지 않는다.</p>
 */
@Getter
@EqualsAndHashCode
public final class NotificationJobRequest {
	private final String occurrenceKey;
	private final NotificationType type;
	private final UUID eventId;
	private final UUID publicationId;
	private final String title;
	private final String body;
	private final String linkUrl;
	private final Instant scheduledAt;
	private final List<UUID> recipientIds;

	/**
	 * 중복·순서·DB 시각 정밀도 차이가 재요청 판정에 영향을 주지 않도록 입력을 정규화한다.
	 *
	 * @throws NotificationProcessingException 필수 값이나 기존 DB 컬럼의 길이 제한을 위반한 경우
	 */
	public NotificationJobRequest(String occurrenceKey, NotificationType type, UUID eventId,
			UUID publicationId, String title, String body, String linkUrl, Instant scheduledAt,
			List<UUID> recipientIds) {
		if (!validText(occurrenceKey, 160) || type == null || !validText(title, 200)
				|| body == null || body.isBlank()
				|| body.getBytes(StandardCharsets.UTF_8).length > 65_535
				|| (linkUrl != null && linkUrl.codePointCount(0, linkUrl.length()) > 500)
				|| scheduledAt == null || recipientIds == null || recipientIds.stream().anyMatch(Objects::isNull)) {
			throw new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);
		}
		this.occurrenceKey = occurrenceKey;
		this.type = type;
		this.eventId = eventId;
		this.publicationId = publicationId;
		this.title = title;
		this.body = body;
		this.linkUrl = linkUrl;
		this.scheduledAt = scheduledAt.truncatedTo(ChronoUnit.MICROS);
		this.recipientIds = recipientIds.stream().distinct()
				.sorted(Comparator.comparing(UUID::toString)).toList();
	}

	private static boolean validText(String value, int maxLength) {
		return value != null && !value.isBlank()
				&& value.codePointCount(0, value.length()) <= maxLength;
	}
}
