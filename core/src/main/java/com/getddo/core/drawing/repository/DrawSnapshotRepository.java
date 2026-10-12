package com.getddo.core.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawSnapshotSource;

/** 모든 조회·저장은 호출자의 같은 트랜잭션 안에서 수행한다. */
public interface DrawSnapshotRepository {
	Optional<DrawSnapshotSource.Event> lockEvent(UUID eventId);
	Optional<DrawSnapshot> findInitial(UUID eventId);
	List<DrawSnapshotSource.Participant> findParticipants(UUID eventId);
	DrawSnapshot saveInitial(DrawSnapshot snapshot);
	/** 호출자는 이벤트를 선잠금한다. 기존 최초 실행이 있으면 그 실행을 반환한다. */
	DrawSnapshot registerInitial(UUID eventId);
	Optional<DrawSnapshot> findRegistered(UUID runId);
	/** 등록된 INITIAL/PREPARING 실행에만 입력을 한 번 저장한다. */
	DrawSnapshot saveRegisteredInitial(DrawSnapshot snapshot);
	/** GD-138 준비 서비스가 기존 최초 후보를 연결하고 버전 2 입력을 한 번 확정한다. */
	DrawSnapshot saveRegisteredReplacement(DrawSnapshot snapshot);
}
