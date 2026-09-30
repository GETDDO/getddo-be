package com.getddo.core.notification.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.notification.domain.Notification;
import com.getddo.core.notification.domain.NotificationErrorCode;
import com.getddo.core.notification.repository.NotificationRepository;

/**
 * 사용자 알림함의 조회와 읽음 처리를 담당한다.
 *
 * <p>헤더로 선택한 등록 사용자의 알림만 다루며, 모의 발송 결과나 읽음 시각은 변경하지 않는다.</p>
 */
@Service
public class NotificationService {
	private final NotificationRepository repository;

	public NotificationService(NotificationRepository repository) {
		this.repository = repository;
	}

	/**
	 * 본인 알림을 커서로 조회한다. 읽기 전용 트랜잭션에서 읽음 상태를 유지한다.
	 *
	 * @param userId 선택한 등록 사용자 ID
	 * @param membership DB 값과 대조할 선택적 멤버십. 생략하면 null
	 * @param cursor 이전 응답의 다음 커서. 첫 조회에서는 null
	 * @param size 조회할 최대 건수. 1~100
	 * @param isRead 읽음 필터. null이면 읽음·미읽음 모두 조회
	 * @return 알림 목록, 다음 커서, 필터에 맞는 전체 건수
	 * @throws BusinessException 사용자·멤버십 또는 조회 조건이 올바르지 않은 경우
	 */
	@Transactional(readOnly = true)
	public CursorResult<Notification> findMine(UUID userId, String membership,
			String cursor, int size, Boolean isRead) {
		requireUser(userId, membership);
		if (size < 1 || size > 100) {
			throw new BusinessException(NotificationErrorCode.INVALID_QUERY);
		}
		return repository.findMine(userId, cursor, size, isRead);
	}

	/**
	 * 본인 알림 한 건을 읽음 처리한다.
	 *
	 * <p>미읽음 행만 갱신한 뒤 변경 행이 없으면 소유권을 확인한다. 이미 읽은 본인 알림은
	 * 재요청을 성공으로 유지하고, 타인 알림과 없는 알림은 동일한 404로 감춘다.</p>
	 *
	 * @param userId 선택한 등록 사용자 ID
	 * @param membership DB 값과 대조할 선택적 멤버십
	 * @param notificationId 읽음 처리할 알림 ID
	 * @return 읽음 상태가 확정된 본인 알림 ID
	 * @throws BusinessException 사용자·멤버십이 올바르지 않거나 본인 알림을 찾을 수 없는 경우
	 */
	@Transactional
	public UUID markRead(UUID userId, String membership, UUID notificationId) {
		requireUser(userId, membership);
		if (repository.markRead(notificationId, userId) == 0
				&& !repository.belongsTo(notificationId, userId)) {
			throw new BusinessException(NotificationErrorCode.NOT_FOUND);
		}
		return notificationId;
	}

	/**
	 * 본인의 미읽음 알림 전체를 쓰기 트랜잭션에서 갱신한다.
	 *
	 * @param userId 선택한 등록 사용자 ID
	 * @param membership DB 값과 대조할 선택적 멤버십
	 * @return 실제 변경 건수. 이미 읽은 알림은 제외
	 * @throws BusinessException 사용자 또는 멤버십이 올바르지 않은 경우
	 */
	@Transactional
	public long markAllRead(UUID userId, String membership) {
		requireUser(userId, membership);
		return repository.markAllRead(userId);
	}

	private void requireUser(UUID userId, String membership) {
		if (userId == null || !repository.userExists(userId)) {
			throw new BusinessException(NotificationErrorCode.USER_CONTEXT_INVALID);
		}
		if (membership != null && !repository.membershipMatches(userId, membership)) {
			throw new BusinessException(NotificationErrorCode.USER_MEMBERSHIP_MISMATCH);
		}
	}
}
