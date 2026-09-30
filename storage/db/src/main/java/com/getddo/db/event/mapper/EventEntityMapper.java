package com.getddo.db.event.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.getddo.core.event.domain.RegisteredEvent;
import com.getddo.db.event.entity.EventEntity;
import com.getddo.db.event.entity.EventPrizeEntity;

/** JPA 저장 결과를 core 모델로 변환한다. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EventEntityMapper {
	@Mapping(target = "prizes", source = "prizes")
	RegisteredEvent toRegisteredEvent(EventEntity event, List<EventPrizeEntity> prizes);

	RegisteredEvent.Prize toPrize(EventPrizeEntity prize);
}
