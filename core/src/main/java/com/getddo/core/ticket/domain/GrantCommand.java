package com.getddo.core.ticket.domain;

import java.util.UUID;

/**
 * 응모권 지급 요청.
 *
 * <p>지급 시각·만료 시각·지갑·멱등키는 호출자가 넘기지 않고 서비스가 정한다.</p>
 *
 * @param userId   지급받을 사용자 ID. 청구 행의 {@code user_id}와 같아야 한다
 * @param source   지급 근거 청구. 같은 트랜잭션에서 먼저 저장되어 있어야 한다
 * @param quantity 지급 수량. 1 이상이며 청구 행의 {@code ticket_count}와 같아야 한다
 * @param reason   이력 화면에 그대로 표시되는 사유(예: 미션 제목). 공백 불가
 */
public record GrantCommand(UUID userId, GrantSource source, long quantity, String reason) {
}
