package com.getddo.core.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.drawing.domain.DrawFailure;
import com.getddo.core.drawing.domain.DrawInspectionView;
import com.getddo.core.drawing.domain.DrawVerification;

/** GD-137 읽기 전용 조회. 실행·결과 저장 및 실패 모델의 소유권을 변경하지 않는다. */
public interface DrawInspectionRepository {
	/** findRun으로 존재를 확인한 실행만 조회한다. 호출자는 같은 조회 트랜잭션을 유지한다. */
	DrawInspectionView.ExecutionSummary findExecutionSummary(UUID runId);
	/** 존재 확인된 실행의 전체 실패 기록을 실패 시각·ID 최신순으로 페이징한다. */
	PageResult<DrawFailure> findFailures(UUID runId, PageQuery page);
	/** 실행에 연결된 기존 감사 행을 생성 시각·ID 최신순으로 페이징한다. */
	PageResult<DrawInspectionView.Audit> findExecutionAudits(UUID runId, PageQuery page);
	boolean eventExists(UUID eventId);
	Optional<DrawInspectionView.Run> findRun(UUID runId);
	PageResult<DrawInspectionView.Run> findRuns(UUID eventId, PageQuery page);
	PageResult<DrawInspectionView.Candidate> findCandidates(UUID runId, PageQuery page);
	List<DrawInspectionView.Candidate> findAllCandidates(UUID runId);
	List<DrawInspectionView.Result> findResults(UUID runId);
	DrawVerification.References findReplacementReferences(DrawInspectionView.Run run);
}
