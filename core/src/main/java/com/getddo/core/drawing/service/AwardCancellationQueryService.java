package com.getddo.core.drawing.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.AwardCancellation;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawType;
import com.getddo.core.drawing.repository.AwardCancellationRepository;
import com.getddo.core.drawing.repository.DrawSnapshotRepository;
import com.getddo.core.user.domain.*;
import com.getddo.core.user.repository.UserRepository;

import static com.getddo.core.drawing.exception.AwardCancellationErrorCode.NOT_FOUND;
import static com.getddo.core.drawing.exception.DrawingErrorCode.*;

/** AD09는 기존 연결을 조회할 뿐 새 취소나 실행을 등록하지 않는다. */
@Service
@RequiredArgsConstructor
public class AwardCancellationQueryService {
	private final AwardCancellationRepository cancellations;
	private final DrawSnapshotRepository snapshots;
	private final UserRepository users;

	@Transactional(readOnly = true)
	public DrawSnapshot findConnected(UUID actorId, UUID cancellationId) {
		if (actorId == null) throw new BusinessException(ADMIN_REQUIRED);
		User user = users.findById(actorId).orElseThrow(() -> new BusinessException(ADMIN_REQUIRED));
		if (user.role() != UserRole.ADMIN || user.status() != UserStatus.ACTIVE) throw new BusinessException(ADMIN_REQUIRED);
		if (cancellationId == null) throw new BusinessException(NOT_FOUND);
		AwardCancellation cancellation = cancellations.findById(cancellationId).orElseThrow(() -> new BusinessException(NOT_FOUND));
		DrawSnapshot run = snapshots.findRegistered(cancellation.replacementRunId()).orElseThrow(() -> new BusinessException(INVALID_RUN));
		if (run.drawType() != DrawType.REPLACEMENT) throw new BusinessException(INVALID_RUN);
		return run;
	}
}
