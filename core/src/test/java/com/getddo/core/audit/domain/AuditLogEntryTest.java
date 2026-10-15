package com.getddo.core.audit.domain;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogEntryTest {
	private static final String ID = "00000000-0000-0000-0000-0000000abcde";
	private static final String TIME = "2026-10-15T00:00:00.123456Z";

	@ParameterizedTest
	@MethodSource("publicCases")
	@DisplayName("확인된 작업과 대상의 필드만 공개하고 원본의 비공개 값은 유지한다")
	void projectsAllowedFields(String action, String target, Map<String, Object> expected) {
		// given
		Map<String, Object> raw = new LinkedHashMap<>(expected);
		raw.put("token", "fixture-secret");
		raw.put("contact", Map.of("phoneNum", "01000000000"));
		AuditLogEntry entry = entry(action, target, raw);
		// when
		AuditLogEntry result = entry.publicView();
		// then
		assertThat(result.getBeforeData()).containsExactlyInAnyOrderEntriesOf(expected);
		assertThat(result.getAfterData()).containsExactlyInAnyOrderEntriesOf(expected);
		assertThat(entry.getBeforeData()).containsKey("token").containsKey("contact");
		assertThat(raw).containsKey("token").containsKey("contact");
		assertThatThrownBy(() -> result.getAfterData().put("x", 1)).isInstanceOf(UnsupportedOperationException.class);
	}

	static Stream<Arguments> publicCases() {
		return Stream.of(
				Arguments.of("EVENT_CANCEL", "EVENT", Map.of("status", "CANCELED", "canceledAt", TIME, "refundedTicketCount", 3L)),
				Arguments.of("EVENT_DELETE", "EVENT", Map.of("deletedAt", TIME)),
				Arguments.of("NOTIFICATION_JOB_RETRY", "NOTIFICATION_JOB", Map.of("status", "FAILED", "attemptCount", 4, "attemptBase", 0)),
				Arguments.of("NOTIFICATION_DELIVERY_RETRY", "NOTIFICATION", Map.of("status", "PENDING", "attemptCount", 4, "attemptBase", 4)),
				Arguments.of("DRAW_EXECUTE", "DRAW_RUN", Map.of("status", "READY", "attemptId", ID)),
				Arguments.of("DRAW_RESUME", "DRAW_RUN", Map.of("status", "FAILED", "attemptId", ID)),
				Arguments.of("AWARD_CANCEL", "DRAW_RESULT", Map.of("isCanceled", true, "cancellationId", ID, "replacementRunId", ID)));
	}

	@ParameterizedTest
	@MethodSource("invalidCases")
	@DisplayName("중첩 객체·잘못된 자료형·범위·업무 상태는 공개하지 않는다")
	void rejectsUnexpectedValues(String action, String target, String field, Object value) {
		// given
		AuditLogEntry entry = entry(action, target, Map.of(field, value));
		// when / then
		assertThat(entry.publicView().getBeforeData()).isNull();
		assertThat(entry.getBeforeData()).containsEntry(field, value);
	}

	static Stream<Arguments> invalidCases() {
		return Stream.of(
				Arguments.of("EVENT_CANCEL", "EVENT", "status", "fixture-secret"),
				Arguments.of("EVENT_CANCEL", "EVENT", "status", Map.of("token", "fixture-secret")),
				Arguments.of("EVENT_CANCEL", "EVENT", "refundedTicketCount", -1L),
				Arguments.of("EVENT_CANCEL", "EVENT", "refundedTicketCount", 1.5),
				Arguments.of("EVENT_CANCEL", "EVENT", "refundedTicketCount", BigInteger.ONE.shiftLeft(64)),
				Arguments.of("EVENT_DELETE", "EVENT", "deletedAt", "not-a-time"),
				Arguments.of("EVENT_DELETE", "EVENT", "deletedAt", List.of(TIME)),
				Arguments.of("NOTIFICATION_JOB_RETRY", "NOTIFICATION_JOB", "status", "SENT"),
				Arguments.of("NOTIFICATION_JOB_RETRY", "NOTIFICATION_JOB", "attemptCount", -1),
				Arguments.of("NOTIFICATION_JOB_RETRY", "NOTIFICATION_JOB", "attemptBase", 2147483648L),
				Arguments.of("NOTIFICATION_DELIVERY_RETRY", "NOTIFICATION", "status", "COMPLETED"),
				Arguments.of("DRAW_EXECUTE", "DRAW_RUN", "attemptId", "1-1-1-1-1"),
				Arguments.of("DRAW_RESUME", "DRAW_RUN", "status", "PENDING"),
				Arguments.of("AWARD_CANCEL", "DRAW_RESULT", "isCanceled", "true"),
				Arguments.of("AWARD_CANCEL", "DRAW_RESULT", "cancellationId", Map.of("id", ID)));
	}

	@Test
	@DisplayName("알 수 없는 작업·대상과 전후 데이터 null을 보존하며 허용 키의 JSON null을 제거하지 않는다")
	void handlesUnknownAndNull() {
		// given / when / then
		assertThat(entry("UNREVIEWED", "EVENT", Map.of("status", "OPEN")).publicView().getBeforeData()).isNull();
		assertThat(entry("EVENT_CANCEL", "OTHER", Map.of("status", "OPEN")).publicView().getBeforeData()).isNull();
		assertThat(entry("EVENT_DELETE", "EVENT", null).publicView().getBeforeData()).isNull();
		assertThat(entry("EVENT_DELETE", "EVENT", Collections.singletonMap("deletedAt", null))
				.publicView().getBeforeData()).containsEntry("deletedAt", null);
	}

	@Test
	@DisplayName("시각과 UUID는 유효한 값만 표준 표현으로 공개한다")
	void normalizesScalarRepresentations() {
		// given / when / then
		assertThat(entry("EVENT_DELETE", "EVENT", Map.of("deletedAt", "2026-10-15T09:00:00+09:00"))
				.publicView().getBeforeData()).containsEntry("deletedAt", "2026-10-15T00:00:00Z");
		assertThat(entry("DRAW_EXECUTE", "DRAW_RUN", Map.of("attemptId", ID.toUpperCase(java.util.Locale.ROOT)))
				.publicView().getBeforeData()).containsEntry("attemptId", ID);
	}

	private static AuditLogEntry entry(String action, String target, Map<String, Object> data) {
		return new AuditLogEntry(UUID.fromString(ID), null, action, target, UUID.fromString(ID),
				null, null, Instant.parse(TIME), data, data);
	}
}
