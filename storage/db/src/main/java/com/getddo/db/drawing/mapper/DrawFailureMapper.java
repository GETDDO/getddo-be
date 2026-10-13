package com.getddo.db.drawing.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import com.getddo.core.drawing.domain.DrawFailure;
import com.getddo.db.drawing.entity.DrawFailureEntity;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DrawFailureMapper {
	/** 저장된 실패 행을 실행 서비스와 조회에 사용하는 불변 실패 모델로 변환한다. */
	DrawFailure toDomain(DrawFailureEntity entity);
}
