package com.getddo.core.common.pagination;

import java.util.List;
import java.util.Objects;

/**
 * 커서 기준으로 조회한 항목, 다음 조회에 사용할 커서와 전체 항목 수다.
 *
 * <p>마지막 결과에는 {@code nextCursor}를 null로 전달한다.</p>
 *
 * @param <T> 조회 결과 항목 타입
 */
public final class CursorResult<T> {

	private final List<T> items;
	private final String nextCursor;
	private final long totalElements;

	/**
	 * @param items         조회한 항목 목록. 변경할 수 없는 복사본으로 보관한다
	 * @param nextCursor    다음 조회에 사용할 커서. 다음 항목이 없으면 null
	 * @param totalElements 조회 조건에 맞는 전체 항목 수
	 * @throws IllegalArgumentException 다음 커서가 빈 문자열이거나 전체 수가 음수인 경우
	 */
	public CursorResult(List<T> items, String nextCursor, long totalElements) {
		List<T> copied = List.copyOf(items);
		if (nextCursor != null && nextCursor.isBlank()) {
			throw new IllegalArgumentException("nextCursor must not be blank");
		}
		if (totalElements < 0) {
			throw new IllegalArgumentException("totalElements must be zero or greater");
		}
		this.items = copied;
		this.nextCursor = nextCursor;
		this.totalElements = totalElements;
	}

	/** 다음 조회에 사용할 커서가 있는지 반환한다. */
	public boolean hasNext() {
		return nextCursor != null;
	}

	/** 조회한 항목의 변경 불가능한 목록. */
	public List<T> getItems() {
		return items;
	}

	/** 다음 조회에 사용할 커서. 다음 항목이 없으면 null. */
	public String getNextCursor() {
		return nextCursor;
	}

	/** 조회 조건에 맞는 전체 항목 수. */
	public long getTotalElements() {
		return totalElements;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof CursorResult<?> that)) {
			return false;
		}
		return totalElements == that.totalElements
				&& items.equals(that.items)
				&& Objects.equals(nextCursor, that.nextCursor);
	}

	@Override
	public int hashCode() {
		return Objects.hash(items, nextCursor, totalElements);
	}

	@Override
	public String toString() {
		return "CursorResult[items=" + items + ", nextCursor=" + nextCursor
				+ ", totalElements=" + totalElements + "]";
	}
}
