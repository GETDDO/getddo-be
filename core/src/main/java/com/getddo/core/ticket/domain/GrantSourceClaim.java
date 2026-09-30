package com.getddo.core.ticket.domain;

import java.util.UUID;

/**
 * 지급 검증에 필요한 청구 행의 값.
 *
 * <p>청구 테이블은 각 도메인이 소유하며 응모권은 ID로 읽기만 한다.</p>
 *
 * @param userId      청구 행의 {@code user_id}
 * @param ticketCount 청구 행의 {@code ticket_count}
 */
public record GrantSourceClaim(UUID userId, long ticketCount) {
}
