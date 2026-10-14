package com.getddo.db.common.pagination;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.getddo.core.common.pagination.PageQuery;

/**
 * 페이지 요청을 저장소 조회로 바꿀 때 오프셋이 int 범위를 넘는 경우를 다룬다.
 *
 * <p>JPA는 오프셋을 int로 바꾸므로 {@code Integer.MAX_VALUE}를 넘는 오프셋은 예외가 난다. 그런 페이지는 항목이 있을 수
 * 없으므로 조회하지 않고, 전체 건수만 확인해 빈 목록으로 돌려준다.</p>
 */
public final class PageOffsets {

	private PageOffsets() {
	}

	/** 요청한 페이지의 시작 위치가 int 범위를 넘어 JPA로 조회할 수 없는지 확인한다. */
	public static boolean exceedsIntRange(PageQuery page) {
		return page.offset() > Integer.MAX_VALUE;
	}

	/**
	 * 요청한 페이지의 Spring Data 조회 조건을 만든다. 오프셋이 int 범위를 넘으면 전체 건수만 얻도록 1건짜리 첫 페이지를 돌려준다.
	 * 이때의 결과 항목은 사용하지 않고 {@link #exceedsIntRange}로 비워야 한다.
	 */
	public static PageRequest toPageRequest(PageQuery page, Sort sort) {
		return exceedsIntRange(page) ? PageRequest.of(0, 1, sort)
				: PageRequest.of(page.getPage() - 1, page.getSize(), sort);
	}
}
