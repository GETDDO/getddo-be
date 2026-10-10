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
}
