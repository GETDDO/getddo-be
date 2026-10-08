package com.getddo.api.event.mapper;

import java.time.Instant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.getddo.api.event.dto.response.AdminEventResponse;
import com.getddo.core.event.domain.RegisteredEvent;

/** 등록 결과를 관리자 응답 형식으로 변환한다. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface EventResponseMapper {
	@Mapping(target = "imageUrl", ignore = true)
	@Mapping(target = "publicationScheduledAt",
			expression = "java(event.endsAt().plus(5, java.time.temporal.ChronoUnit.MINUTES))")
	@Mapping(target = "canceledAt", ignore = true)
	@Mapping(target = "prizeImages", source = "event.prizes")
	AdminEventResponse toResponse(RegisteredEvent event, Instant serverTime);

	@Mapping(target = "imageUrl", ignore = true)
	AdminEventResponse.Prize toPrize(RegisteredEvent.Prize prize);

	@Mapping(target = "prizeId", source = "id")
	AdminEventResponse.PrizeImage toPrizeImage(RegisteredEvent.Prize prize);
}
