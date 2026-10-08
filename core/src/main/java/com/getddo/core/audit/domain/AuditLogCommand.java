package com.getddo.core.audit.domain;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;

/**
 * 업무 서비스가 감사 기록 저장을 요청할 때 전달하는 변경 정보.
 *
 * <p>변경을 자동으로 감지하거나 DB에 저장하지 않는다. 호출하는 업무 서비스가 처리자와 대상,
 * 기록할 변경 전후 값을 선택한다. Entity나 요청 본문 전체 대신 기록에 필요한 필드만 전달한다.</p>
 *
 * <p>final 필드만으로는 Map 내부까지 불변이 되지 않으므로 생성 시 중첩 Map·List·배열까지 복사한다.
 * 원본을 나중에 수정해도 생성 당시 값이 유지되며, 접근자로 반환한 중첩 컬렉션도 변경할 수 없다.</p>
 */
@Getter
public final class AuditLogCommand {

	private final UUID actorId;
	private final String action;
	private final String targetType;
	private final UUID targetId;
	private final String reason;
	private final Map<String, Object> beforeData;
	private final Map<String, Object> afterData;
	private final String requestId;

	/**
	 * 필수값과 저장 가능한 데이터 형식을 확인하고 변경 전후 값의 스냅샷을 만든다.
	 *
	 * @param actorId 검증된 사용자 문맥에서 가져온 처리자 ID. 시스템 작업이면 null
	 * @param action 수행한 작업의 코드. 필수이며 공백 불가, 최대 60자
	 * @param targetType 변경 대상의 종류를 나타내는 코드. 필수이며 공백 불가, 최대 60자
	 * @param targetId 변경 대상의 ID. 필수이며 대상의 존재 여부는 호출하는 업무 서비스가 확인
	 * @param reason 처리 사유. 전달할 사유가 없으면 null
	 * @param beforeData 변경 전 값 중 기록할 필드. 전달할 데이터가 없으면 null
	 * @param afterData 변경 후 값 중 기록할 필드. 전달할 데이터가 없으면 null
	 * @param requestId 관련 요청을 추적하기 위한 선택 ID. 최대 100자이며 중복 저장 방지 키가 아님
	 * @throws IllegalArgumentException 필수값 누락, 문자열 길이 초과 또는 지원하지 않는 데이터가 있는 경우
	 */
	public AuditLogCommand(UUID actorId, String action, String targetType, UUID targetId,
			String reason, Map<String, Object> beforeData, Map<String, Object> afterData, String requestId) {
		validateText(action, "action", 60, true);
		validateText(targetType, "targetType", 60, true);
		validateText(requestId, "requestId", 100, false);
		if (targetId == null) {
			throw new IllegalArgumentException("targetId is required");
		}
		this.actorId = actorId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.reason = reason;
		this.beforeData = snapshot(beforeData);
		this.afterData = snapshot(afterData);
		this.requestId = requestId;
	}

	private static void validateText(String value, String field, int maximum, boolean required) {
		if (required && (value == null || value.isBlank())) {
			throw new IllegalArgumentException(field + " is required");
		}
		// MySQL utf8mb4의 문자 수에 맞춰 보조 평면 문자도 한 글자로 센다.
		if (value != null && value.codePointCount(0, value.length()) > maximum) {
			throw new IllegalArgumentException(field + " exceeds " + maximum + " characters");
		}
	}

	/**
	 * null은 데이터 미제공으로 유지하고, Map은 내부 값까지 복사해 변경할 수 없게 보관한다.
	 * 값이 같은 서로 다른 객체를 순환으로 오인하지 않도록 참조 자체를 비교하는 집합을 사용한다.
	 */
	@SuppressWarnings("unchecked")
	private static Map<String, Object> snapshot(Map<String, Object> source) {
		if (source == null) {
			return null;
		}
		return (Map<String, Object>) copyJson(source, Collections.newSetFromMap(new IdentityHashMap<>()));
	}

	/**
	 * 지원하는 JSON 값만 복사하며 배열은 변경 불가능한 List로 변환한다.
	 * 임의 객체, 문자열이 아닌 Map 키, NaN·무한대와 순환 참조는 저장 요청 전에 거절한다.
	 *
	 * <p>현재 탐색 경로에서 같은 객체를 다시 만날 때만 순환으로 판단한다.
	 * 서로 다른 필드가 같은 하위 객체를 가리키는 정상적인 반복 참조는 허용한다.</p>
	 */
	private static Object copyJson(Object value, Set<Object> path) {
		// null과 아래 불변 값은 내부 상태가 바뀌지 않으므로 새 객체로 복사할 필요가 없다.
		// BigInteger·BigDecimal은 가변 상태를 가진 하위 타입이 섞이지 않도록 정확한 클래스만 허용한다.
		if (value == null || value instanceof String || value instanceof Boolean
				|| value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
				|| value.getClass() == BigInteger.class || value.getClass() == BigDecimal.class) {
			return value;
		}
		// 실수도 그대로 사용하되, JSON 숫자로 표현할 수 없는 NaN과 무한대는 통과시키지 않는다.
		if (value instanceof Double doubleValue && Double.isFinite(doubleValue)
				|| value instanceof Float floatValue && Float.isFinite(floatValue)) {
			return value;
		}
		// 기본값 외에는 Map·List·배열만 지원한다. 임의 객체와 위에서 통과하지 못한 NaN·무한대는 거절한다.
		if (!(value instanceof Map<?, ?> || value instanceof List<?> || value.getClass().isArray())) {
			throw new IllegalArgumentException("Audit data must contain only JSON values");
		}
		// path는 전체 방문 기록이 아니라 현재 재귀 호출 경로다. 참조가 같은 객체를 다시 만나면
		// 자기 자신을 포함하는 Map처럼 순환하는 구조이므로 무한 재귀에 빠지기 전에 거절한다.
		if (!path.add(value)) {
			throw new IllegalArgumentException("Audit data must not contain cyclic references");
		}
		try {
			if (value instanceof Map<?, ?> map) {
				// 원본과 분리된 Map을 만들고, 원본을 순회한 순서대로 항목을 보관한다.
				Map<String, Object> copy = new LinkedHashMap<>();
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					// JSON 객체의 키는 문자열이어야 한다. 다른 타입의 키를 임의로 문자열로 바꾸지 않는다.
					if (!(entry.getKey() instanceof String key)) {
						throw new IllegalArgumentException("Audit data object keys must be strings");
					}
					// 안쪽 Map·List·배열도 재귀적으로 복사해 중첩된 원본 객체가 공유되지 않게 한다.
					copy.put(key, copyJson(entry.getValue(), path));
				}
				// 복사본의 수정을 막으면서 JSON 내부의 null은 보존한다. Map.copyOf는 null을 거절한다.
				return Collections.unmodifiableMap(copy);
			}
			// Map 처리는 위에서 끝났으므로 남은 List와 배열은 모두 List로 복사해 JSON 배열로 표현한다.
			List<Object> copy = new ArrayList<>();
			if (value instanceof List<?> list) {
				// 목록의 순서를 유지하고 각 항목의 중첩 데이터까지 복사한다.
				for (Object item : list) {
					copy.add(copyJson(item, path));
				}
			} else {
				// int[] 같은 기본형 배열은 Object[]로 캐스팅할 수 없어 Array API로 원소를 읽는다.
				for (int i = 0; i < Array.getLength(value); i++) {
					copy.add(copyJson(Array.get(value, i), path));
				}
			}
			// 목록 안의 null을 보존하면서 호출자가 원소를 추가·교체·삭제하지 못하게 한다.
			return Collections.unmodifiableList(copy);
		} finally {
			// 현재 경로에서만 제거한다. 두 필드가 같은 하위 객체를 공유하는 경우는 순환이 아니므로
			// 다음 필드에서 그 객체를 만나면 정상적으로 다시 복사할 수 있어야 한다.
			path.remove(value);
		}
	}
}
