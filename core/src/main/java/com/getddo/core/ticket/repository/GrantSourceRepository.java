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
	 * 청구 행의 사용자와 수량을 쓰기 잠금으로 조회한다.
	 *
	 * <p>같은 청구로 동시에 지급을 시도하는 다른 트랜잭션은 이 잠금이 풀릴 때(커밋·롤백)까지 기다린다.
	 * 그래서 먼저 지급한 트랜잭션이 커밋한 뒤에야 기존 응모권을 조회하고, 그 결과를 돌려받을 수 있다.</p>
	 *
	 * @param source 청구 종류와 ID
	 * @return 청구 값. 청구 행이 없으면 빈 값
	 */
	Optional<GrantSourceClaim> findForUpdate(GrantSource source);
}
