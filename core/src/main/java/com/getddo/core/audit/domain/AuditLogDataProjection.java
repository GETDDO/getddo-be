package com.getddo.core.audit.domain;

import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.notification.domain.MockDeliveryStatus;
import com.getddo.core.notification.domain.NotificationJobStatus;

/** 공용 감사 조회의 허용 목록이다. 임의 JSON과 알 수 없는 업무를 그대로 공개하지 않는다. */
final class AuditLogDataProjection {
	private AuditLogDataProjection() {
	}

	static Map<String, Object> project(String action, String targetType, Map<String, Object> source) {
		if (source == null) return null;
		Set<String> fields = switch (action + ":" + targetType) {
			case "EVENT_CANCEL:EVENT" -> Set.of("status", "canceledAt", "refundedTicketCount");
			case "EVENT_DELETE:EVENT" -> Set.of("deletedAt");
			case "NOTIFICATION_JOB_RETRY:NOTIFICATION_JOB", "NOTIFICATION_DELIVERY_RETRY:NOTIFICATION" ->
					Set.of("status", "attemptCount", "attemptBase");
			case "DRAW_EXECUTE:DRAW_RUN", "DRAW_RESUME:DRAW_RUN" -> Set.of("status", "attemptId");
			case "AWARD_CANCEL:DRAW_RESULT" -> Set.of("isCanceled", "cancellationId", "replacementRunId");
			default -> Set.of();
		};
		Map<String, Object> result = new LinkedHashMap<>();
		for (String field : fields) {
			if (source.containsKey(field) && allowed(action, field, source.get(field))) {
				result.put(field, normalize(field, source.get(field)));
			}
		}
		return result.isEmpty() ? null : result;
	}

	private static boolean allowed(String action, String field, Object value) {
		if (value == null) return true;
		return switch (field) {
			case "status" -> validStatus(action, value);
			case "attemptCount", "attemptBase" -> nonnegativeInteger(value, Integer.MAX_VALUE);
			case "refundedTicketCount" -> nonnegativeInteger(value, Long.MAX_VALUE);
			case "isCanceled" -> value instanceof Boolean;
			case "deletedAt", "canceledAt" -> instant(value);
			case "attemptId", "cancellationId", "replacementRunId" -> uuid(value);
			default -> false;
		};
	}

	private static boolean validStatus(String action, Object value) {
		return switch (action) {
			case "EVENT_CANCEL" -> enumValue(EventStatus.class, value);
			case "NOTIFICATION_JOB_RETRY" -> enumValue(NotificationJobStatus.class, value);
			case "NOTIFICATION_DELIVERY_RETRY" -> enumValue(MockDeliveryStatus.class, value);
			case "DRAW_EXECUTE", "DRAW_RESUME" -> enumValue(DrawRunStatus.class, value);
			default -> false;
		};
	}

	private static <E extends Enum<E>> boolean enumValue(Class<E> type, Object value) {
		return value instanceof String code && Arrays.stream(type.getEnumConstants()).anyMatch(item -> item.name().equals(code));
	}

	private static boolean nonnegativeInteger(Object value, long maximum) {
		if (value instanceof BigInteger number) {
			return number.signum() >= 0 && number.compareTo(BigInteger.valueOf(maximum)) <= 0;
		}
		return (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)
				&& ((Number) value).longValue() >= 0 && ((Number) value).longValue() <= maximum;
	}

	private static boolean instant(Object value) {
		if (!(value instanceof String text)) return false;
		try {
			Instant.parse(text);
			return true;
		} catch (DateTimeParseException exception) {
			return false;
		}
	}

	private static boolean uuid(Object value) {
		if (!(value instanceof String text)) return false;
		try {
			return UUID.fromString(text).toString().equalsIgnoreCase(text);
		} catch (IllegalArgumentException exception) {
			return false;
		}
	}

	private static Object normalize(String field, Object value) {
		if (value == null) return null;
		return switch (field) {
			case "deletedAt", "canceledAt" -> Instant.parse((String) value).toString();
			case "attemptId", "cancellationId", "replacementRunId" -> UUID.fromString((String) value).toString();
			default -> value;
		};
	}
}
