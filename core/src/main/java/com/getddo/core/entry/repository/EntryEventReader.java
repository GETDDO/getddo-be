package com.getddo.core.entry.repository;

import java.util.Optional;
import java.util.UUID;

import com.getddo.core.entry.domain.EntryEvent;

/** 응모 판단에 필요한 이벤트 정보를 읽는다. 이벤트의 저장·수정은 이벤트 도메인이 맡는다. */
public interface EntryEventReader {

	/** 이벤트를 잠그지 않고 읽는다. 삭제된 이벤트도 {@code deleted=true}로 돌려준다. */
	Optional<EntryEvent> find(UUID eventId);

	/**
	 * 이벤트 행을 공유 잠금({@code FOR SHARE})으로 잠가 읽는다. 호출자 트랜잭션 안에서만 호출한다.
	 *
	 * <p>응모끼리는 서로 막지 않고, 추첨 준비가 이 행을 배타 잠금으로 잠그면 진행 중인 응모가 커밋될 때까지 기다린다.</p>
	 */
	Optional<EntryEvent> findForShare(UUID eventId);
}
