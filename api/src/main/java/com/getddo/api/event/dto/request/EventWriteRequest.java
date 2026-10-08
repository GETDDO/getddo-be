package com.getddo.api.event.dto.request;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.event.exception.EventErrorCode;

public record EventWriteRequest(
		@NotBlank @Size(max = 200) String title,
		@NotBlank String description,
		@Size(max = 500) String imageKey,
		@NotNull EventType eventType,
		@NotNull Boolean weightingEnabled,
		Integer maxTicketsPerUser,
		@NotNull MembershipRule membershipRule,
		@NotNull OffsetDateTime startsAt,
		@NotNull OffsetDateTime endsAt,
		@NotEmpty List<@NotNull @Valid PrizeWrite> prizes
) {
	public EventRegistration toRegistration(UUID actorId) {
		return new EventRegistration(actorId, title, description, imageKey, eventType,
				Boolean.TRUE.equals(weightingEnabled), maxTicketsPerUser, membershipRule,
				startsAt == null ? null : startsAt.toInstant(), endsAt == null ? null : endsAt.toInstant(),
				prizes == null ? null : prizes.stream().map(PrizeWrite::toPrize).toList());
	}

	public record PrizeWrite(
			UUID id,
			@Positive int rank,
			@NotBlank @Size(max = 200) String name,
			String description,
			@Size(max = 500) String imageKey,
			@Positive int winnerCount
	) {
		EventRegistration.Prize toPrize() {
			if (id != null) {
				throw new BusinessException(EventErrorCode.INVALID_PRIZES);
			}
			return new EventRegistration.Prize(rank, name, description, imageKey, winnerCount);
		}
	}
}
