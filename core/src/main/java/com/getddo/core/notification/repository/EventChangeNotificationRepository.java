package com.getddo.core.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.notification.domain.EventNotificationChange;

/** 이벤트는 변경하지 않고 원본 시작 작업과 이미 생성된 알림을 연결한다. */
public interface EventChangeNotificationRepository {
	/** 시작 생산자와 같은 이벤트 행을 잠근다. 호출 Service의 업무 트랜잭션에 참여한다. */
	void lockEvent(UUID eventId);
	/** 재요청 확인은 최신 잠금 읽기로 수행한다. */
	Optional<UUID> findChangeJobId(String occurrenceKey);
	/** 저장된 이벤트가 전달된 변경·취소 상태인지 확인한다. */
	boolean matchesSavedChange(EventNotificationChange change);
	/** 시작 작업의 향후 생성을 막는다. 작업·알림 이력을 삭제하지 않는다. */
	void stopStartJobs(UUID eventId, String occurrenceKey);
	/** 시작 알림이 알림함에 생성된 사용자만 반환한다. 발송·읽음 상태는 조건이 아니다. */
	List<UUID> findStartRecipientIds(UUID eventId);
	/** 기존 source_job_id 컬럼에 시작 작업 근거를 연결한다. */
	void linkSourceJob(UUID jobId, UUID eventId);
}
