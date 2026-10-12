package com.getddo.core.drawing.service;

import java.util.List;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.*;
import com.getddo.core.drawing.repository.DrawCheckRepository;
import com.getddo.core.drawing.repository.DrawInspectionRepository;
import com.getddo.core.user.domain.User;
import com.getddo.core.user.domain.UserRole;
import com.getddo.core.user.domain.UserStatus;

import static com.getddo.core.drawing.exception.DrawInspectionErrorCode.*;
import static com.getddo.core.drawing.exception.DrawingErrorCode.EVENT_NOT_FOUND;

/** 조회와 검증 이력 저장의 업무 트랜잭션을 관리한다. 공통 실행 기능을 호출하지 않는다. */
@Service
@RequiredArgsConstructor
public class DrawInspectionService {
	private final DrawInspectionRepository repository;
	private final DrawCheckRepository checks;
	private final TimeProvider time;

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<DrawRunView> runs(User user, UUID eventId, PageQuery page) {
		admin(user);
		if (!repository.eventExists(eventId)) throw new BusinessException(EVENT_NOT_FOUND);
		return repository.findRuns(eventId, page);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public DrawRunView run(User user, UUID runId) {
		admin(user);
		return requireRun(runId);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public DrawRunDetailView detail(User user, UUID runId) {
		admin(user);
		DrawRunView run = requireRun(runId);
		return new DrawRunDetailView(run, repository.findAllCandidates(runId), repository.findResults(runId));
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<DrawCandidateView> candidates(User user, UUID runId, PageQuery page) {
		admin(user);
		requireRun(runId);
		return repository.findCandidates(runId, page);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public List<DrawResultView> results(User user, UUID runId) {
		admin(user);
		requireRun(runId);
		return repository.findResults(runId);
	}

	/** 불일치도 검증 결과로 저장한다. 실행·후보·조건·결과에는 쓰기를 수행하지 않는다. */
	@Transactional(isolation = Isolation.REPEATABLE_READ)
	public DrawVerification verify(User user, UUID runId) {
		admin(user);
		DrawRunView run = requireRun(runId);
		if (run.getStatus() != DrawRunStatus.CONFIRMED) throw new BusinessException(CHECK_NOT_ALLOWED);
		List<DrawCheckItem> items = DrawStoredVerification.verify(run,
				repository.findAllCandidates(runId), repository.findResults(runId),
				run.getDrawType() == DrawType.REPLACEMENT ? repository.findReplacementReferences(run)
						: DrawVerificationReferences.empty());
		return checks.save(runId, user.id(), time.now(), items);
	}

	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResult<DrawVerification> history(User user, UUID runId, PageQuery page) {
		admin(user);
		requireRun(runId);
		return checks.findByRun(runId, page);
	}

	private DrawRunView requireRun(UUID runId) {
		return repository.findRun(runId).orElseThrow(() -> new BusinessException(RUN_NOT_FOUND));
	}

	/** API가 공통 사용자 문맥에서 확인한 DB 사용자를 재사용한다. */
	private void admin(User user) {
		if (user == null || user.id() == null || user.role() != UserRole.ADMIN || user.status() != UserStatus.ACTIVE) {
			throw new BusinessException(ADMIN_REQUIRED);
		}
	}
}
