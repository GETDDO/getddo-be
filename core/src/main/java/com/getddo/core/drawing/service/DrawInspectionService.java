package com.getddo.core.drawing.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.DrawFailure;
import com.getddo.core.drawing.domain.DrawInspectionView;
import com.getddo.core.drawing.domain.DrawRunStatus;
import com.getddo.core.drawing.domain.DrawSnapshotSource;
import com.getddo.core.drawing.domain.DrawType;
import com.getddo.core.drawing.domain.DrawVerification;
import com.getddo.core.drawing.repository.DrawCheckRepository;
import com.getddo.core.drawing.repository.DrawInspectionRepository;
import com.getddo.core.user.domain.User;

import static com.getddo.core.drawing.exception.DrawInspectionErrorCode.*;
import static com.getddo.core.drawing.exception.DrawingErrorCode.EVENT_NOT_FOUND;

/** 관리자에게 저장된 실행·후보·결과를 조회해 주고, 규칙 검사 결과를 검증 이력에 남긴다. */
@Service
@RequiredArgsConstructor
public class DrawInspectionService {
	private final DrawInspectionRepository repository;
	private final DrawCheckRepository checks;
	private final TimeProvider time;

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<DrawInspectionView.Run> runs(User user, UUID eventId, PageQuery page) {
		admin(user);
		if (!repository.eventExists(eventId)) throw new BusinessException(EVENT_NOT_FOUND);
		return repository.findRuns(eventId, page);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public DrawInspectionView.Run run(User user, UUID runId) {
		admin(user);
		return requireRun(runId);
	}

	/** 하나의 읽기 트랜잭션에서 실행·결과와 실패·취소 요약을 함께 조회한다. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public DrawInspectionView.Detail detail(User user, UUID runId) {
		admin(user);
		DrawInspectionView.Run run = requireRun(runId);
		return new DrawInspectionView.Detail(run, orderedResults(run), repository.findExecutionSummary(runId));
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<DrawInspectionView.Candidate> candidates(User user, UUID runId, PageQuery page) {
		admin(user);
		requireRun(runId);
		return repository.findCandidates(runId, page);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public List<DrawInspectionView.Result> results(User user, UUID runId) {
		admin(user);
		DrawInspectionView.Run run = requireRun(runId);
		return orderedResults(run);
	}

	/** 관리자 권한과 실행 존재를 확인한 뒤 전체 실패 이력의 한 페이지를 반환한다. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<DrawFailure> failures(User user, UUID runId, PageQuery page) {
		admin(user);
		requireRun(runId);
		return repository.findFailures(runId, page);
	}

	/** 관리자 권한과 실행 존재를 확인한 뒤 원본 감사 행을 변경하지 않고 조회한다. */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<DrawInspectionView.Audit> executionAudits(User user, UUID runId, PageQuery page) {
		admin(user);
		requireRun(runId);
		return repository.findExecutionAudits(runId, page);
	}

	/** 불일치도 검증 결과로 저장한다. 실행·후보·조건·결과에는 쓰기를 수행하지 않는다. */
	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public DrawVerification verify(User user, UUID runId) {
		admin(user);
		DrawInspectionView.Run run = requireRun(runId);
		if (run.getStatus() != DrawRunStatus.CONFIRMED) throw new BusinessException(CHECK_NOT_ALLOWED);
		List<DrawVerification.Item> items = DrawStoredVerification.verify(run,
				repository.findAllCandidates(runId), repository.findResults(runId),
				run.getDrawType() == DrawType.REPLACEMENT ? repository.findReplacementReferences(run)
				: DrawVerification.References.empty());
		boolean passed = items.stream().allMatch(DrawVerification.Item::isPassed);
		return checks.save(runId, user.getId(), time.now(), passed, items);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<DrawVerification> history(User user, UUID runId, PageQuery page) {
		admin(user);
		requireRun(runId);
		return checks.findByRun(runId, page);
	}

	/** 현재 경품 등수가 바뀌어도 응답 순서는 저장된 추첨 조건의 등수·자리 순이다. */
	private List<DrawInspectionView.Result> orderedResults(DrawInspectionView.Run run) {
		Map<UUID, Integer> ranks = new HashMap<>();
		if (run.getRules() != null) {
			for (DrawSnapshotSource.Prize prize : run.getRules().prizes()) ranks.put(prize.id(), prize.rank());
		}
		return repository.findResults(run.getId()).stream().sorted(Comparator
				.comparingInt((DrawInspectionView.Result result) -> ranks.getOrDefault(result.getPrizeId(), Integer.MAX_VALUE))
				.thenComparingInt(DrawInspectionView.Result::getSlotNumber)).toList();
	}

	private DrawInspectionView.Run requireRun(UUID runId) {
		return repository.findRun(runId).orElseThrow(() -> new BusinessException(RUN_NOT_FOUND));
	}

	/** API가 공통 사용자 문맥에서 확인한 DB 사용자를 재사용한다. */
	private void admin(User user) {
		DrawAdminAuthorization.validate(user, ADMIN_REQUIRED);
	}
}
