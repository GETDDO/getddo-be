package com.getddo.db.drawing.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import com.getddo.core.drawing.domain.DrawFailure;
import com.getddo.db.drawing.entity.DrawFailureEntity;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DrawFailureMapper {
	DrawFailure toDomain(DrawFailureEntity entity);
}
