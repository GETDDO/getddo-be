package com.getddo.db.drawing.mapper;

import org.springframework.stereotype.Component;

import com.getddo.core.drawing.domain.AwardCancellation;
import com.getddo.db.drawing.entity.AwardCancellationEntity;
import com.getddo.core.common.exception.BusinessException;
import static com.getddo.core.drawing.exception.DrawingErrorCode.INVALID_RUN;

@Component
public class AwardCancellationMapper {
	public AwardCancellation toDomain(AwardCancellationEntity entity) {
		// 기존 DDL의 미연결 이력은 새 실행으로 추정하지 않고 명시적인 상태 충돌로 거절한다.
		if (entity.getReplacementRunId() == null) throw new BusinessException(INVALID_RUN);
		return new AwardCancellation(entity.getId(), entity.getDrawResultId(), entity.getReplacementRunId(),
				entity.getCanceledBy(), entity.getReason(), entity.getCanceledAt(), entity.getRequestKey());
	}
}
