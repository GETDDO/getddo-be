package com.getddo.core.ticket.repository;

import java.util.Optional;

import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceClaim;

/**
 * 지급 근거 청구 조회. 청구 테이블은 미션·출석·게임이 각각 소유하며 응모권은 읽기만 한다.
 *
 * <p>호출자가 같은 트랜잭션에서 방금 저장한 청구도 보여야 한다.</p>
 */
public interface GrantSourceRepository {

	/**
	 * 청구 행의 사용자와 수량을 조회한다.
	 *
	 * @param source 청구 종류와 ID
	 * @return 청구 값. 청구 행이 없으면 빈 값
	 */
	Optional<GrantSourceClaim> find(GrantSource source);
}
