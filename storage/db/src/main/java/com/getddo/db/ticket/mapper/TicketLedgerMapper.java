package com.getddo.db.ticket.mapper;

import java.util.UUID;

import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketLedger;
import com.getddo.core.ticket.domain.TicketLedgerAllocation;
import com.getddo.db.ticket.entity.TicketLedgerAllocationEntity;
import com.getddo.db.ticket.entity.TicketLedgerEntity;

/**
 * 원장·배분 Entity와 도메인 객체 변환.
 *
 * <p>도메인의 지급 근거 청구 하나는 원장의 청구 종류별 참조 컬럼 셋 중 하나로 저장된다.</p>
 */
public final class TicketLedgerMapper {

	private TicketLedgerMapper() {
	}

	public static TicketLedgerEntity toEntity(TicketLedger ledger) {
		GrantSource source = ledger.getGrantSource();
		return TicketLedgerEntity.builder()
				.walletId(ledger.getWalletId())
				.userId(ledger.getUserId())
				.missionRewardClaimId(claimIdOf(source, GrantSourceType.MISSION))
				.attendanceRewardClaimId(claimIdOf(source, GrantSourceType.ATTENDANCE))
				.gameRewardClaimId(claimIdOf(source, GrantSourceType.GAME))
				.expiresAt(ledger.getExpiresAt())
				.transactionType(ledger.getType())
				.quantity(ledger.getQuantity())
				.idempotencyKey(ledger.getIdempotencyKey())
				.reason(ledger.getReason())
				.createdAt(ledger.getCreatedAt())
				.balanceAfter(ledger.getBalanceAfter())
				.walletVersion(ledger.getWalletVersion())
				.build();
	}

	public static TicketLedger toDomain(TicketLedgerEntity entity) {
		return new TicketLedger(
				entity.getId(),
				entity.getWalletId(),
				entity.getUserId(),
				entity.getTransactionType(),
				entity.getQuantity(),
				entity.getIdempotencyKey(),
				entity.getReason(),
				entity.getCreatedAt(),
				entity.getBalanceAfter(),
				entity.getWalletVersion(),
				entity.getExpiresAt(),
				grantSourceOf(entity));
	}

	public static TicketLedgerAllocationEntity toEntity(TicketLedgerAllocation allocation) {
		return new TicketLedgerAllocationEntity(
				allocation.getLedgerId(),
				allocation.getSourceCreditLedgerId(),
				allocation.getOriginalGrantId(),
				allocation.getQuantity(),
				allocation.getCreatedAt());
	}

	private static UUID claimIdOf(GrantSource source, GrantSourceType type) {
		if (source == null || source.getType() != type) {
			return null;
		}
		return source.getClaimId();
	}

	private static GrantSource grantSourceOf(TicketLedgerEntity entity) {
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
