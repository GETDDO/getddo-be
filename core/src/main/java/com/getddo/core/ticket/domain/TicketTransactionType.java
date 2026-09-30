package com.getddo.core.ticket.domain;

/**
 * 응모권 원장 거래 유형. {@code ticket_ledger.transaction_type} ENUM과 같은 값을 가진다.
 *
 * <p>GRANT·REFUND는 양수, SPEND·EXPIRE·REVOKE는 음수, CORRECTION은 0이 아닌 수량을 기록한다
 * ({@code chk_ledger_sign}).</p>
 */
public enum TicketTransactionType {
	GRANT,
	SPEND,
	REFUND,
	EXPIRE,
	REVOKE,
	CORRECTION
}
