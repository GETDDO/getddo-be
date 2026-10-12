package com.getddo.db.drawing.mapper;

import org.springframework.stereotype.Component;

import com.getddo.core.drawing.domain.AwardCancellation;
import com.getddo.db.drawing.entity.AwardCancellationEntity;

@Component
public class AwardCancellationMapper {
	public AwardCancellation toDomain(AwardCancellationEntity entity) {
		return new AwardCancellation(entity.getId(), entity.getDrawResultId(), entity.getReplacementRunId(),
				entity.getCanceledBy(), entity.getReason(), entity.getCanceledAt(), entity.getRequestKey());
	}
}
