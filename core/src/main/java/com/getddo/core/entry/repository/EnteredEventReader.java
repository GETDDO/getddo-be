package com.getddo.core.entry.repository;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** 사용자가 접수 완료된 응모를 가진 이벤트를 읽는다. 읽기 전용이며 아무것도 잠그지 않는다. */
public interface EnteredEventReader {

	/**
	 * 주어진 이벤트 중 사용자가 접수 완료된 응모를 한 번이라도 한 이벤트의 ID를 한 번의 조회로 돌려준다. 거절된 요청은
	 * 응모한 것으로 보지 않는다.
	 *
	 * @param eventIds 비어 있지 않은 이벤트 ID 목록
	 * @return 응모한 이벤트 ID. 없으면 빈 집합
	 */
	Set<UUID> findEnteredEventIds(UUID userId, Collection<UUID> eventIds);
}
