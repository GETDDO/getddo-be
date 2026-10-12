package com.getddo.core.drawing.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.drawing.domain.DrawCheckItem;
import com.getddo.core.drawing.domain.DrawVerification;

/** 원본 자료와 별도로 항목별 검증 결과를 보존한다. */
public interface DrawCheckRepository {
	DrawVerification save(UUID runId, UUID actorId, Instant checkedAt, List<DrawCheckItem> checks);
	PageResult<DrawVerification> findByRun(UUID runId, PageQuery page);
}
