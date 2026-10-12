package com.getddo.core.drawing.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.Set;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;

import com.getddo.core.drawing.domain.DrawPublication;
import com.getddo.core.drawing.domain.DrawPublishedSlot;
import com.getddo.core.drawing.domain.DrawPublicationRun;
import com.getddo.core.drawing.domain.DrawPublicationCancellation;
import com.getddo.core.drawing.domain.DrawResultConfirmation;

/** 공개 전용 저장소. 변경은 서비스가 이벤트 선잠금을 가진 트랜잭션에서 수행한다. */
public interface DrawPublicationRepository {
	Optional<DrawPublication> findLatest(UUID eventId);
	List<DrawPublication> findHistory(UUID eventId);
	PageResult<DrawPublication> findHistory(UUID eventId, PageQuery query);
	Optional<DrawPublication> findPrevious(UUID eventId, int revision);
	boolean eventExists(UUID eventId);
	List<DrawPublishedSlot> findPublishedSlots(UUID publicationId);
	Map<UUID, List<DrawPublishedSlot>> findPublishedSlots(Set<UUID> publicationIds);
	boolean wasPublished(UUID resultId);
	/** 정상 최초 공개의 빠른 경로와 취소·재추첨 명단 구성 경로를 구분한다. */
	boolean hasCancellationOrReplacement(UUID eventId);
	DrawPublication saveInitial(UUID eventId, UUID runId, List<UUID> selectedResultIds, Instant publishedAt);
	Optional<UUID> findEventId(UUID runId);
	/** 이벤트 잠금 이후 공개에 필요한 기존 실행과 결과만 조회한다. 상태·입력·취소를 변경하지 않는다. */
	List<DrawPublicationRun> findReplacementRuns(UUID eventId);
	List<DrawPublicationCancellation> findCancellations(UUID eventId);
	DrawResultConfirmation saveConfirmation(UUID runId, UUID actorId, Instant confirmedAt);
	DrawPublication savePublication(UUID eventId, UUID sourceRunId, int revision, String reason,
			DrawResultConfirmation confirmation, List<DrawPublication.Entry> entries, Instant publishedAt);
}
