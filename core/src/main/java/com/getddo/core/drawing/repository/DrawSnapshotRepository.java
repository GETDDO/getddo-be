package com.getddo.core.drawing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawSnapshotSource;

/** 모든 조회·저장은 호출자의 같은 트랜잭션 안에서 수행한다. */
public interface DrawSnapshotRepository {
	/** 입력 준비와 실행 처리의 공통 선잠금 대상인 이벤트 원본을 읽는다. */
	Optional<DrawSnapshotSource.Event> lockEvent(UUID eventId);
	/** 이벤트의 최초 실행과 해당 실행에 저장된 조건·후보를 조회한다. */
	Optional<DrawSnapshot> findInitial(UUID eventId);
	/** 이벤트의 응모자와 접수·차감 근거를 읽어 서비스의 수량·가중치 검증에 제공한다. */
	List<DrawSnapshotSource.Participant> findParticipants(UUID eventId);
	/** 새 최초 실행과 후보·조건 스냅샷을 호출자의 트랜잭션에서 함께 저장한다. */
	DrawSnapshot saveInitial(DrawSnapshot snapshot);
	/** 호출자는 이벤트를 선잠금한다. 기존 최초 실행이 있으면 그 실행을 반환한다. */
	DrawSnapshot registerInitial(UUID eventId);
	/** 등록된 실행 ID에 저장된 상태·조건·후보를 읽는다. 입력 고정 전 실행도 조회할 수 있다. */
	Optional<DrawSnapshot> findRegistered(UUID runId);
	/** 등록된 INITIAL/PREPARING 실행에만 입력을 한 번 저장한다. */
	DrawSnapshot saveRegisteredInitial(DrawSnapshot snapshot);
	/** GD-138 준비 서비스가 기존 최초 후보를 연결하고 버전 2 입력을 한 번 확정한다. */
	DrawSnapshot saveRegisteredReplacement(DrawSnapshot snapshot);
}
