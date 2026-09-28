package com.getddo.core.common.pagination;

import java.util.List;

/**
 * 커서 기준으로 조회한 항목, 다음 조회에 사용할 커서와 전체 항목 수다.
 *
 * <p>마지막 결과에는 {@code nextCursor}를 null로 전달한다.</p>
 *
 * @param items 조회한 항목의 변경 불가능한 목록
 * @param nextCursor 다음 조회에 사용할 커서. 다음 항목이 없으면 null
 * @param totalElements 조회 조건에 맞는 전체 항목 수
 * @param <T> 조회 결과 항목 타입
 */
public record CursorResult<T>(List<T> items, String nextCursor, long totalElements) {

	public CursorResult {
		items = List.copyOf(items);
		if (nextCursor != null && nextCursor.isBlank()) {
			throw new IllegalArgumentException("nextCursor must not be blank");
		}
		if (totalElements < 0) {
			throw new IllegalArgumentException("totalElements must be zero or greater");
		}
	}

	/** 다음 조회에 사용할 커서가 있는지 반환한다. */
	public boolean hasNext() {
		return nextCursor != null;
	}
}
