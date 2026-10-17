package com.getddo.core.entry.service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.entry.repository.EnteredEventReader;

/**
 * 이벤트 목록에 보여 줄 "내가 응모한 이벤트" 조회. 이벤트마다 응모 현황(E03)을 따로 부르지 않고 목록의 이벤트를 한 번에
 * 확인한다. 이벤트 목록(E01)을 만드는 이벤트 도메인이 이 서비스를 호출한다.
 */
@Service
@RequiredArgsConstructor
public class MyEntryStatusService {

	private final EnteredEventReader enteredEventReader;

	/**
	 * 주어진 이벤트 중 사용자가 응모한 이벤트의 ID를 돌려준다. 로그인하지 않은 요청({@code userId}가 null)과 이벤트 목록이 빈
	 * 경우는 저장소를 읽지 않고 빈 집합이다.
	 *
	 * <p>이 서비스는 사용자의 역할을 검사하지 않고 주어진 {@code userId}의 응모 기록만 읽는다. 관리자는 응모할 수 없어 보통
	 * 기록이 없지만 서비스가 그것을 보장하지는 않는다. 관리자 요청에 {@code myEntered=false}를 적용하는 일은 이벤트 목록(E01)
	 * 호출자가 한다.</p>
	 *
	 * @param userId 요청 사용자. 비로그인이면 null
	 * @param eventIds 화면에 보여 줄 이벤트 ID
	 */
	@Transactional(readOnly = true)
	public Set<UUID> findEnteredEventIds(UUID userId, Collection<UUID> eventIds) {
		if (userId == null || eventIds == null || eventIds.isEmpty()) {
			return Set.of();
		}
		return enteredEventReader.findEnteredEventIds(userId, eventIds);
	}
}
