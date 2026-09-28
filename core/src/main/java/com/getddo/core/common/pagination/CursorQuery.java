package com.getddo.core.common.pagination;

/**
 * 커서 기준 목록 조회 요청이다.
 *
 * <p>첫 조회에는 {@code cursor}를 null로 전달한다. 커서의 형식과 정렬 기준은
 * 실제 목록을 조회하는 기능에서 정한다.</p>
 *
 * @param cursor 이전 조회에서 받은 커서. 첫 조회에서는 null
 * @param size 조회할 최대 항목 수
 */
public record CursorQuery(String cursor, int size) {

	public CursorQuery {
		if (cursor != null && cursor.isBlank()) {
			throw new IllegalArgumentException("cursor must not be blank");
		}
		if (size < 1) {
			throw new IllegalArgumentException("size must be positive");
		}
	}
}
