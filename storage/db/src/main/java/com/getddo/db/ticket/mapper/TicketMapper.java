package com.getddo.db.ticket.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.Ticket;
import com.getddo.core.ticket.domain.TicketHistory;
import com.getddo.db.ticket.entity.TicketEntity;
import com.getddo.db.ticket.entity.TicketHistoryEntity;

/**
 * 응모권·이력 Entity와 도메인 객체 변환.
 *
 * <p>도메인의 지급 근거 청구 하나는 응모권의 청구 종류별 참조 컬럼 셋 중 하나로 저장된다. 이 분배만 직접 작성한다.</p>
 */
@Mapper(componentModel = "spring", imports = GrantSourceType.class)
public interface TicketMapper {

	@Mapping(target = "attendanceRewardClaimId",
			expression = "java(claimIdOf(ticket.getGrantSource(), GrantSourceType.ATTENDANCE))")
	@Mapping(target = "missionRewardClaimId",
			expression = "java(claimIdOf(ticket.getGrantSource(), GrantSourceType.MISSION))")
	@Mapping(target = "gameRewardClaimId",
			expression = "java(claimIdOf(ticket.getGrantSource(), GrantSourceType.GAME))")
	TicketEntity toEntity(Ticket ticket);

	@Mapping(target = "grantSource", expression = "java(grantSourceOf(entity))")
	Ticket toDomain(TicketEntity entity);

	TicketHistoryEntity toEntity(TicketHistory history);

	TicketHistory toDomain(TicketHistoryEntity entity);

	/** 청구가 해당 종류일 때만 그 청구 ID를 돌려준다. */
	default UUID claimIdOf(GrantSource source, GrantSourceType type) {
		if (source == null || source.getType() != type) {
			return null;
		}
		return source.getClaimId();
	}

	/** 채워진 청구 참조 컬럼으로 지급 근거 청구를 만든다. */
	default GrantSource grantSourceOf(TicketEntity entity) {
		if (entity.getMissionRewardClaimId() != null) {
			return new GrantSource(GrantSourceType.MISSION, entity.getMissionRewardClaimId());
		}
		if (entity.getAttendanceRewardClaimId() != null) {
			return new GrantSource(GrantSourceType.ATTENDANCE, entity.getAttendanceRewardClaimId());
		}
		if (entity.getGameRewardClaimId() != null) {
			return new GrantSource(GrantSourceType.GAME, entity.getGameRewardClaimId());
		}
		throw new IllegalStateException("응모권에 지급 근거 청구가 없다.");
	}
}
