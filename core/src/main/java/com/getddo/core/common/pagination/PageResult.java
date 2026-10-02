package com.getddo.core.common.pagination;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 한 페이지의 항목과 전체 조회 결과의 개수를 담는다.
 *
 * @param <T> 조회 결과 항목 타입
 */
public final class PageResult<T> {

	private final List<T> items;
	private final int page;
	private final int size;
	private final long totalElements;

	/**
	 * @param items         현재 페이지의 항목 목록. 변경할 수 없는 복사본으로 보관한다
	 * @param page          1부터 시작하는 현재 페이지 번호
	 * @param size          페이지당 최대 항목 수
	 * @param totalElements 조회 조건에 맞는 전체 항목 수
	 * @throws IllegalArgumentException 페이지 조건이 올바르지 않거나, 항목 수가 크기를 넘거나, 전체 수가 음수인 경우
	 */
	public PageResult(List<T> items, int page, int size, long totalElements) {
		new PageQuery(page, size);
		List<T> copied = List.copyOf(items);
		if (copied.size() > size) {
			throw new IllegalArgumentException("items cannot exceed page size");
		}
		if (totalElements < 0) {
			throw new IllegalArgumentException("totalElements must be zero or greater");
		}
		this.items = copied;
		this.page = page;
		this.size = size;
		this.totalElements = totalElements;
	}

	/**
	 * 항목의 순서와 페이지 정보를 유지하면서 각 항목을 변환한 새 결과를 반환한다.
	 *
	 * @param mapper 각 항목에 적용할 변환 함수
	 * @param <R> 변환한 항목 타입
	 * @throws NullPointerException 변환 함수가 null이거나 변환한 항목이 null인 경우
	 */
	public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
		Objects.requireNonNull(mapper, "mapper");
		List<R> mapped = items.stream().<R>map(mapper::apply).toList();
		return new PageResult<>(mapped, page, size, totalElements);
	}

	/** 전체 항목 수를 기준으로 계산한 페이지 수를 반환한다. */
	public long totalPages() {
		return totalElements / size + (totalElements % size == 0 ? 0 : 1);
	}

	/** 현재 페이지 뒤에 다른 페이지가 있는지 반환한다. */
	public boolean hasNext() {
		return page < totalPages();
	}

	/** 현재 페이지의 변경 불가능한 항목 목록. */
	public List<T> getItems() {
		return items;
	}

	public int getPage() {
		return page;
	}

	public int getSize() {
		return size;
	}

	public long getTotalElements() {
		return totalElements;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PageResult<?> that)) {
			return false;
		}
		return page == that.page && size == that.size && totalElements == that.totalElements
				&& items.equals(that.items);
	}

	@Override
	public int hashCode() {
		return Objects.hash(items, page, size, totalElements);
	}

	@Override
	public String toString() {
		return "PageResult[items=" + items + ", page=" + page + ", size=" + size
				+ ", totalElements=" + totalElements + "]";
	}
}
