package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 응모권 원장 행. 수정·삭제 없이 누적되는 거래 이력이다.
 *
 * <p>현재는 지급(GRANT)에 필요한 값만 담는다. 차감·반환·회수 등에서 쓰는 참조는 해당 기능에서 추가한다.</p>
 *
 * @param id             원장 ID. 저장 전에는 null
 * @param walletId       거래가 반영된 지갑 ID
 * @param userId         지갑 소유 사용자 ID
 * @param type           거래 유형
 * @param quantity       수량 변동. 증가는 양수, 감소는 음수
 * @param idempotencyKey 원장 행별 고정 키
 * @param reason         처리 사유
 * @param createdAt      거래 시각 UTC
 * @param balanceAfter   거래 직후 지갑 잔액
 * @param walletVersion  거래로 갱신된 지갑 version
 * @param expiresAt      입금(GRANT·REFUND)분의 만료 시각. 그 외 null
 * @param grantSource    지급 근거 청구. GRANT가 아니면 null
 */
public record TicketLedger(
		UUID id,
		UUID walletId,
		UUID userId,
		TicketTransactionType type,
		long quantity,
		String idempotencyKey,
		String reason,
		Instant createdAt,
		long balanceAfter,
		long walletVersion,
		Instant expiresAt,
		GrantSource grantSource) {

	/**
	 * 입금이 반영된 지갑으로 지급 원장 행을 만든다.
	 *
	 * <p>{@code createdAt}은 지갑 월과 만료 시각을 계산한 지급 시각과 같아야 한다.
	 * 그래야 원장의 생성 시각, 적용 만료일, 응답의 지급 시각이 서로 어긋나지 않는다.</p>
	 *
	 * @param depositedWallet 이번 지급을 반영한 뒤의 지갑
	 * @param command         지급 요청
	 * @param grantedAt       지급 시각
	 * @return 저장 전 지급 원장 행
	 */
	public static TicketLedger grant(TicketWallet depositedWallet, GrantCommand command, Instant grantedAt) {
		GrantSource source = command.source();
		return new TicketLedger(
				null,
				depositedWallet.id(),
				depositedWallet.userId(),
				TicketTransactionType.GRANT,
				command.quantity(),
				source.idempotencyKey(),
				command.reason(),
				grantedAt,
				depositedWallet.balance(),
				depositedWallet.version(),
				depositedWallet.expiresAt(),
				source);
	}

	/**
	 * 이 지급 원장 행을 호출자에게 돌려줄 결과로 바꾼다.
	 *
	 * @param replayed 이전에 확정된 지급을 돌려주는 경우 true
	 * @return 지급 결과
	 */
	public GrantResult toGrantResult(boolean replayed) {
		return new GrantResult(id, walletId, quantity, balanceAfter, createdAt, expiresAt, replayed);
	}
}
