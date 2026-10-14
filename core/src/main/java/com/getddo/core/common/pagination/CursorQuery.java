package com.getddo.core.common.pagination;

import java.util.Objects;

/**
 * 커서 기준 목록 조회 요청이다.
 *
 * <p>첫 조회에는 {@code cursor}를 null로 전달한다. 커서의 형식과 정렬 기준은
 * 실제 목록을 조회하는 기능에서 정한다.</p>
 */
public final class CursorQuery {

	private final String cursor;
	private final int size;

	/**
	 * @param cursor 이전 조회에서 받은 커서. 첫 조회에서는 null
	 * @param size   조회할 최대 항목 수
	 * @throws IllegalArgumentException 커서가 빈 문자열이거나 크기가 1보다 작은 경우
	 */
	public CursorQuery(String cursor, int size) {
		if (cursor != null && cursor.isBlank()) {
			throw new IllegalArgumentException("cursor must not be blank");
		}
		if (size < 1) {
			throw new IllegalArgumentException("size must be positive");
		}
		this.cursor = cursor;
		this.size = size;
	}

	/** 이전 조회에서 받은 커서. 첫 조회면 null. */
	public String getCursor() {
		return cursor;
	}

	/** 조회할 최대 항목 수. */
	public int getSize() {
		return size;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof CursorQuery that)) {
			return false;
		}
		return size == that.size && Objects.equals(cursor, that.cursor);
	}

	@Override
	public int hashCode() {
		return Objects.hash(cursor, size);
	}

	@Override
	public String toString() {
		return "CursorQuery[cursor=" + cursor + ", size=" + size + "]";
	}
}
