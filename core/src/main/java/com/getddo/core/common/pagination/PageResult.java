package com.getddo.core.common.pagination;

import java.util.List;

/**
 * 한 페이지의 항목과 전체 조회 결과의 개수를 담는다.
 *
 * @param items 현재 페이지의 변경 불가능한 항목 목록
 * @param page 1부터 시작하는 현재 페이지 번호
 * @param size 페이지당 최대 항목 수
 * @param totalElements 조회 조건에 맞는 전체 항목 수
 * @param <T> 조회 결과 항목 타입
 */
public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

	public PageResult {
		new PageQuery(page, size);
		items = List.copyOf(items);
		if (items.size() > size) {
			throw new IllegalArgumentException("items cannot exceed page size");
		}
		if (totalElements < 0) {
			throw new IllegalArgumentException("totalElements must be zero or greater");
		}
	}

	/** 전체 항목 수를 기준으로 계산한 페이지 수를 반환한다. */
	public long totalPages() {
		return totalElements / size + (totalElements % size == 0 ? 0 : 1);
	}

	/** 현재 페이지 뒤에 다른 페이지가 있는지 반환한다. */
	public boolean hasNext() {
		return page < totalPages();
	}
}
