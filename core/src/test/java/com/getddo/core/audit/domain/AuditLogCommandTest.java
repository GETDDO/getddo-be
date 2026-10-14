package com.getddo.core.audit.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogCommandTest {

	private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000551");

	@Test
	@DisplayName("필수 문자열의 최대 길이와 선택 값의 null을 허용한다")
	void acceptsBoundariesAndNulls() {
		// given / when
		AuditLogCommand command = new AuditLogCommand(null, "가".repeat(60), "😀".repeat(60),
				TARGET_ID, null, null, null, "가".repeat(100));
		// then
		assertThat(command.getActorId()).isNull();
		assertThat(command.getAction()).isEqualTo("가".repeat(60));
		assertThat(command.getTargetType()).isEqualTo("😀".repeat(60));
		assertThat(command.getTargetId()).isEqualTo(TARGET_ID);
		assertThat(command.getReason()).isNull();
		assertThat(command.getBeforeData()).isNull();
		assertThat(command.getAfterData()).isNull();
		assertThat(command.getRequestId()).hasSize(100);
		assertThat(command(null, null).getRequestId()).isNull();
	}

	@ParameterizedTest
	@MethodSource("invalidFields")
	@DisplayName("필수값 누락과 스키마 문자열 길이 초과를 거절한다")
	void rejectsInvalidFields(String action, String targetType, UUID targetId, String requestId) {
		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> new AuditLogCommand(
				null, action, targetType, targetId, null, null, null, requestId));
	}

	static Stream<Arguments> invalidFields() {
		return Stream.of(
				Arguments.of(null, "TEST", TARGET_ID, null),
				Arguments.of(" ", "TEST", TARGET_ID, null),
				Arguments.of("a".repeat(61), "TEST", TARGET_ID, null),
				Arguments.of("CHANGE", null, TARGET_ID, null),
				Arguments.of("CHANGE", "", TARGET_ID, null),
				Arguments.of("CHANGE", "😀".repeat(61), TARGET_ID, null),
				Arguments.of("CHANGE", "TEST", null, null),
				Arguments.of("CHANGE", "TEST", TARGET_ID, "r".repeat(101)));
	}

	@Test
	@SuppressWarnings("unchecked")
	@DisplayName("중첩 Map·List·배열의 원본 변경과 접근자를 통한 변경으로부터 기록을 보호한다")
	void capturesNestedDataSnapshot() {
		// given
		Map<String, Object> child = new LinkedHashMap<>();
		child.put("status", "ACTIVE");
		child.put("optional", null);
		List<Object> list = new ArrayList<>(Arrays.asList(child, null));
		int[] counts = {1, 2};
		Map<String, Object> source = new LinkedHashMap<>();
		source.put("items", list);
		source.put("counts", counts);
		AuditLogCommand command = command(source, source);
		// when
		child.put("status", "CHANGED");
		list.clear();
		counts[0] = 99;
		source.clear();
		// then
		Map<String, Object> expectedChild = new LinkedHashMap<>();
		expectedChild.put("status", "ACTIVE");
		expectedChild.put("optional", null);
		assertThat(command.getBeforeData()).containsEntry("items", Arrays.asList(expectedChild, null))
				.containsEntry("counts", List.of(1, 2));
		assertThat(command.getAfterData()).isEqualTo(command.getBeforeData());
		List<Object> savedList = (List<Object>) command.getBeforeData().get("items");
		Map<String, Object> savedChild = (Map<String, Object>) savedList.get(0);
		assertThatThrownBy(() -> command.getBeforeData().put("new", true))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> savedList.add("new")).isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> savedChild.put("status", "new"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("JSON 숫자·문자열·불리언을 보존하고 같은 객체의 반복 참조를 허용한다")
	void acceptsJsonScalarsAndSharedChildren() {
		// given
		Map<String, Object> child = Map.of("values", List.of("text", true, (byte) 1, (short) 2,
				3, 4L, 1.5F, 2.5D, new BigInteger("12345678901234567890"), new BigDecimal("1.2345")));
		Map<String, Object> source = Map.of("first", child, "second", child);
		// when / then
		assertThat(command(source, null).getBeforeData()).isEqualTo(source);
	}

	@ParameterizedTest
	@MethodSource("invalidJsonValues")
	@DisplayName("임의 객체·가변 숫자·비유한 숫자와 문자열이 아닌 JSON 키를 거절한다")
	void rejectsNonJsonValues(Object value) {
		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> command(Map.of("value", value), null));
		assertThatIllegalArgumentException().isThrownBy(() -> command(null, Map.of("value", value)));
	}

	static Stream<Object> invalidJsonValues() {
		Map<Object, Object> nullKey = new LinkedHashMap<>();
		nullKey.put(null, "value");
		return Stream.of(new Object(), Instant.EPOCH, new AtomicInteger(1), Double.NaN,
				Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Map.of(1, "value"), nullKey);
	}

	@Test
	@DisplayName("Map·List·배열의 순환 참조를 저장 전에 거절한다")
	void rejectsCycles() {
		// given
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("self", map);
		List<Object> list = new ArrayList<>();
		list.add(list);
		Object[] array = new Object[1];
		array[0] = array;
		// when / then
		assertThatIllegalArgumentException().isThrownBy(() -> command(map, null));
		assertThatIllegalArgumentException().isThrownBy(() -> command(Map.of("list", list), null));
		assertThatIllegalArgumentException().isThrownBy(() -> command(Map.of("array", array), null));
	}

	private AuditLogCommand command(Map<String, Object> before, Map<String, Object> after) {
		return new AuditLogCommand(null, "TEST_CHANGE", "TEST", TARGET_ID, null, before, after, null);
	}
}
