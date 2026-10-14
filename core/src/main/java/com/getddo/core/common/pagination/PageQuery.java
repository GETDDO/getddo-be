package com.getddo.core.common.pagination;

import java.util.Objects;

/**
 * 1부터 시작하는 페이지 번호와 페이지당 최대 항목 수다.
 *
 * <p>기본 크기와 최대 크기는 목록 API의 요구사항에 맞춰 정한다.</p>
 */
public final class PageQuery {

	private final int page;
	private final int size;

	/**
	 * @param page 1부터 시작하는 페이지 번호
	 * @param size 페이지당 최대 항목 수
	 * @throws IllegalArgumentException 페이지 번호나 크기가 1보다 작은 경우
	 */
	public PageQuery(int page, int size) {
		if (page < 1) {
			throw new IllegalArgumentException("page must be positive");
		}
		if (size < 1) {
			throw new IllegalArgumentException("size must be positive");
		}
		this.page = page;
		this.size = size;
	}

	/** 저장소 조회에 사용할 0부터 시작하는 항목 위치를 반환한다. */
	public long offset() {
		return ((long) page - 1) * size;
	}

	/** 1부터 시작하는 페이지 번호. */
	public int getPage() {
		return page;
	}

	/** 페이지당 최대 항목 수. */
	public int getSize() {
		return size;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PageQuery that)) {
			return false;
		}
		return page == that.page && size == that.size;
	}

	@Override
	public int hashCode() {
		return Objects.hash(page, size);
	}

	@Override
	public String toString() {
		return "PageQuery[page=" + page + ", size=" + size + "]";
	}
}
