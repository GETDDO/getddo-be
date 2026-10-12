package com.getddo.core.drawing.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.drawing.domain.DrawConfirmation;
import com.getddo.core.drawing.domain.DrawSelection;
import com.getddo.core.drawing.domain.DrawSnapshot;

/** 이벤트 선잠금을 보유한 서비스의 같은 트랜잭션에서 결과를 확정한다. */
public interface DrawResultRepository {
	/** 이벤트를 먼저 잠그기 위해 실행의 소속 이벤트를 조회한다. 실행이 없으면 빈 Optional을 반환한다. */
	Optional<UUID> findEventId(UUID runId);
	/** 이벤트 잠금 이후 실행을 쓰기 잠금으로 조회하고 저장된 후보·규칙 스냅샷을 반환한다. */
	Optional<DrawSnapshot> lockSnapshot(UUID runId);
	/** 실행·연결 후보·참가자·경품의 이벤트와 후보 사용자 일치를 검증한다. 원본 조건은 재계산하지 않는다. */
	void validateRelations(DrawSnapshot snapshot);
	/** 활성 시도는 해당 토큰으로만 확정한다. 토큰 없는 기존 READY 확정 호출은 유지한다. */
	void validateAttempt(UUID runId, UUID attemptId);
	/** READY 실행에 비정상적인 부분 결과가 남아 있는지 확인하여 기존 결과의 덮어쓰기를 방지한다. */
	boolean hasResults(UUID runId);
	/** 확정된 선정 결과 중 취소 이력에 연결되지 않은 현재 유효 당첨 배정을 조회한다. */
	List<DrawSelection> findCurrentAwards(UUID eventId);
	/** 저장된 결과 ID·시각·순서를 복원한다. 재요청에서도 선정이나 공개 처리를 수행하지 않는다. */
	DrawConfirmation findConfirmed(DrawSnapshot snapshot);
	/** 서비스가 시작한 트랜잭션에서 결과와 실행 확정을 저장한다. 최초 응답에도 실제 DB 저장 시각을 반환한다. */
	DrawConfirmation saveConfirmed(DrawSnapshot snapshot, List<DrawSelection> selections, Instant confirmedAt);
}
