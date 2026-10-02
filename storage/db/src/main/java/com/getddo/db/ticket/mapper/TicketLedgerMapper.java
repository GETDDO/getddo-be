package com.getddo.db.ticket.mapper;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketLedger;
import com.getddo.core.ticket.domain.TicketLedgerAllocation;
import com.getddo.db.ticket.entity.TicketLedgerAllocationEntity;
import com.getddo.db.ticket.entity.TicketLedgerEntity;

/**
 * 원장·배분 Entity와 도메인 객체 변환.
 *
 * <p>도메인의 지급 근거 청구 하나는 원장의 청구 종류별 참조 컬럼 셋 중 하나로 저장된다. 이 분배만 직접 작성한다.</p>
 */
@Mapper(componentModel = "spring", imports = GrantSourceType.class)
public interface TicketLedgerMapper {

	@Mapping(target = "transactionType", source = "type")
	@Mapping(target = "missionRewardClaimId",
			expression = "java(claimIdOf(ledger.getGrantSource(), GrantSourceType.MISSION))")
	@Mapping(target = "attendanceRewardClaimId",
			expression = "java(claimIdOf(ledger.getGrantSource(), GrantSourceType.ATTENDANCE))")
	@Mapping(target = "gameRewardClaimId",
			expression = "java(claimIdOf(ledger.getGrantSource(), GrantSourceType.GAME))")
	TicketLedgerEntity toEntity(TicketLedger ledger);

	@Mapping(target = "type", source = "transactionType")
	@Mapping(target = "grantSource", expression = "java(grantSourceOf(entity))")
	TicketLedger toDomain(TicketLedgerEntity entity);

	TicketLedgerAllocationEntity toEntity(TicketLedgerAllocation allocation);

	/** 청구가 해당 종류일 때만 그 청구 ID를 돌려준다. */
	default UUID claimIdOf(GrantSource source, GrantSourceType type) {
		if (source == null || source.getType() != type) {
			return null;
		}
		return source.getClaimId();
	}

	/** 채워진 청구 참조 컬럼으로 지급 근거 청구를 만든다. 지급이 아닌 원장이면 null이다. */
	default GrantSource grantSourceOf(TicketLedgerEntity entity) {
		if (entity.getMissionRewardClaimId() != null) {
			return new GrantSource(GrantSourceType.MISSION, entity.getMissionRewardClaimId());
		}
		if (entity.getAttendanceRewardClaimId() != null) {
			return new GrantSource(GrantSourceType.ATTENDANCE, entity.getAttendanceRewardClaimId());
		}
		if (entity.getGameRewardClaimId() != null) {
			return new GrantSource(GrantSourceType.GAME, entity.getGameRewardClaimId());
		}
		return null;
	}
}
