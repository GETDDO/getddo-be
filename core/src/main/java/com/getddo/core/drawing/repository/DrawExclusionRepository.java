package com.getddo.core.drawing.repository;

import java.util.Set;
import java.util.UUID;

/**
 * 이벤트 잠금을 보유한 트랜잭션에서 확정된 제외 응모자 ID를 조회한다.
 * 미검토 의심이나 부정 확정만으로 제외를 추정하지 않는다. 저장 근거 연결은 후속 작업이다.
 */
public interface DrawExclusionRepository {
	Set<UUID> findExcludedParticipantIds(UUID eventId);
}
