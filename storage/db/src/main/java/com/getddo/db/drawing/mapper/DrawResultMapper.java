package com.getddo.db.drawing.mapper;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.domain.DrawResult;
import com.getddo.core.drawing.domain.DrawSelection;
import com.getddo.core.drawing.domain.DrawSelectionResultType;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.db.drawing.entity.DrawResultEntity;

import static com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN;

/** 변경 가능한 현재 경품 정보 대신 저장된 규칙·후보에서 결과를 복원한다. */
@Component
public class DrawResultMapper {
	public DrawResult toDomain(DrawResultEntity entity, DrawSnapshot snapshot) {
		var prize = snapshot.rules().prizes().stream().filter(p -> p.id().equals(entity.getPrizeId()))
				.findFirst().orElseThrow(() -> new BusinessException(INVALID_RUN));
		UUID userId = null;
		if (entity.getResultType() == DrawSelectionResultType.SELECTED) {
			userId = snapshot.candidates().stream().filter(c -> c.id().equals(entity.getCandidateId()))
					.findFirst().orElseThrow(() -> new BusinessException(INVALID_RUN)).userId();
		}
		return new DrawResult(entity.getId(), new DrawSelection(entity.getPrizeId(), prize.rank(), entity.getSlotNumber(),
				entity.getSelectionOrder(), entity.getCandidateId(), userId, entity.getResultType()), entity.getCreatedAt());
	}
}
