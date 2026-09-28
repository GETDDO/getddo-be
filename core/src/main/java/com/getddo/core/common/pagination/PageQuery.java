package com.getddo.core.common.pagination;

/**
 * 1부터 시작하는 페이지 번호와 페이지당 최대 항목 수다.
 *
 * <p>기본 크기와 최대 크기는 목록 API의 요구사항에 맞춰 정한다.</p>
 *
 * @param page 1부터 시작하는 페이지 번호
 * @param size 페이지당 최대 항목 수
 */
public record PageQuery(int page, int size) {

	public PageQuery {
		if (page < 1) {
			throw new IllegalArgumentException("page must be positive");
		}
		if (size < 1) {
			throw new IllegalArgumentException("size must be positive");
		}
	}

	/** 저장소 조회에 사용할 0부터 시작하는 항목 위치를 반환한다. */
	public long offset() {
		return ((long) page - 1) * size;
	}
}
