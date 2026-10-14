package com.getddo.core.drawing.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawPublicationConfirmation;
import com.getddo.core.drawing.domain.DrawPublicationRun;
import com.getddo.core.drawing.domain.DrawResultConfirmation;
import com.getddo.core.drawing.repository.DrawPublicationRepository;

import static com.getddo.core.drawing.exception.DrawPublicationErrorCode.*;

/** 관리자의 재추첨 결과 확인을 기록하고, 공개 가능한 시점이면 명단에 반영한다. */
@RequiredArgsConstructor
public class DrawResultConfirmationService {
	private final DrawPublicationRepository repository;
	private final DrawPublicationService publications;
	private final DrawAdminAuthorization admins;
	private final TimeProvider time;

	/**
	 * 이벤트 선잠금 아래 기존 확정 결과를 확인하고 공개를 반영한다. 최초 예정 시각 전에는 확인만 저장한다.
	 * 같은 실행의 확인 재요청은 원래 확인자·시각을 반환한다. 공개 저장 실패는 새 확인도 롤백한다.
	 * 변경 근거는 GD-138이 보존한 연결 취소 사유를 사용하며 알림 생성·이벤트 상태 변경은 하지 않는다.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public DrawResultConfirmation confirmReplacement(UUID runId, UUID actorId) {
		DrawPublicationRun run = authorizeAndLock(runId, actorId);
		return finish(run, actorId).getConfirmation();
	}

	/** 최초 공개 전에 확인한다. 공개 후 재요청은 이미 저장된 확인의 재시도만 허용한다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public DrawPublicationConfirmation confirmBeforeInitial(UUID runId, UUID actorId) {
		DrawPublicationRun run = authorizeAndLock(runId, actorId);
		if (repository.findLatest(run.getEventId()).isPresent() && run.getConfirmation() == null)
		throw new BusinessException(INVALID_PHASE);
		return finish(run, actorId);
	}

	/** AD06은 최초 공개가 존재할 때만 새 결과를 확인하고 최신 공개 버전을 반환한다. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public DrawPublication confirmForPublication(UUID runId, UUID actorId) {
		DrawPublicationRun run = authorizeAndLock(runId, actorId);
		if (repository.findLatest(run.getEventId()).isEmpty()) throw new BusinessException(INVALID_PHASE);
		return finish(run, actorId).getPublication();
	}

	/** 활성 관리자와 실행 존재를 확인한 뒤 이벤트 선잠금 아래 재추첨 결과를 검증한다. */
	private DrawPublicationRun authorizeAndLock(UUID runId, UUID actorId) {
		admins.requireActiveAdmin(actorId, ADMIN_REQUIRED);
		if (runId == null) throw new BusinessException(RUN_NOT_FOUND);
		UUID eventId = repository.findEventId(runId).orElseThrow(() -> new BusinessException(RUN_NOT_FOUND));
		return publications.validateReplacement(eventId, runId);
	}

	/** 기존 확인을 재사용하거나 새 확인을 저장하고 공개 가능한 전체 명단을 반영한다. */
	private DrawPublicationConfirmation finish(DrawPublicationRun run, UUID actorId) {
		DrawResultConfirmation confirmation = run.getConfirmation();
		if (confirmation == null) confirmation = repository.saveConfirmation(run.getRunId(), actorId, time.now());
		DrawPublication publication = publications.publishConfirmed(run.getEventId(), run.getRunId()).orElse(null);
		return new DrawPublicationConfirmation(run.getEventId(), confirmation, publication);
	}
}
