package com.getddo.core.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.drawing.domain.DrawCandidateView;
import com.getddo.core.drawing.domain.DrawExecutionSummary;
import com.getddo.core.drawing.domain.DrawExecutionAuditView;
import com.getddo.core.drawing.domain.DrawFailure;
import com.getddo.core.drawing.domain.DrawResultView;
import com.getddo.core.drawing.domain.DrawRunView;
import com.getddo.core.drawing.domain.DrawVerificationReferences;

/** GD-137 읽기 전용 조회. 실행·결과 저장 및 실패 모델의 소유권을 변경하지 않는다. */
public interface DrawInspectionRepository {
	/** findRun으로 존재를 확인한 실행만 조회한다. 호출자는 같은 조회 트랜잭션을 유지한다. */
	DrawExecutionSummary findExecutionSummary(UUID runId);
	PageResult<DrawFailure> findFailures(UUID runId, PageQuery page);
	PageResult<DrawExecutionAuditView> findExecutionAudits(UUID runId, PageQuery page);
	boolean eventExists(UUID eventId);
	Optional<DrawRunView> findRun(UUID runId);
	PageResult<DrawRunView> findRuns(UUID eventId, PageQuery page);
	PageResult<DrawCandidateView> findCandidates(UUID runId, PageQuery page);
	List<DrawCandidateView> findAllCandidates(UUID runId);
	List<DrawResultView> findResults(UUID runId);
	DrawVerificationReferences findReplacementReferences(DrawRunView run);
}
