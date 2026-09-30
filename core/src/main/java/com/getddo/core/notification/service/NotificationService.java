package com.getddo.core.notification.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.pagination.CursorResult;
import com.getddo.core.notification.domain.Notification;
import com.getddo.core.notification.domain.NotificationErrorCode;
import com.getddo.core.notification.repository.NotificationRepository;
import com.getddo.core.user.domain.User;

/**
 * 사용자 알림함의 조회와 읽음 처리를 담당한다.
 *
 * <p>공통 사용자 처리에서 확인한 사용자의 알림만 다룬다. 사용자·멤버십을 다시 조회하지 않으며,
 * 모의 발송 결과나 읽음 시각은 변경하지 않는다.</p>
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
	 * @param user 공통 사용자 처리에서 확인한 등록 사용자
	 * @param cursor 이전 응답의 다음 커서. 첫 조회에서는 null
	 * @param size 조회할 최대 건수. 1~100
	 * @param isRead 읽음 필터. null이면 읽음·미읽음 모두 조회
	 * @return 알림 목록, 다음 커서, 필터에 맞는 전체 건수
	 * @throws BusinessException 조회 조건이 올바르지 않은 경우
	 */
	@Transactional(readOnly = true)
	public CursorResult<Notification> findMine(User user,
			String cursor, int size, Boolean isRead) {
		if (size < 1 || size > 100) {
			throw new BusinessException(NotificationErrorCode.INVALID_QUERY);
		}
		return repository.findMine(user.id(), cursor, size, isRead);
	}

	/**
	 * 본인 알림 한 건을 읽음 처리한다.
	 *
	 * <p>미읽음 행만 갱신한 뒤 변경 행이 없으면 소유권을 확인한다. 이미 읽은 본인 알림은
	 * 재요청을 성공으로 유지하고, 타인 알림과 없는 알림은 동일한 404로 감춘다.</p>
	 *
	 * @param user 공통 사용자 처리에서 확인한 등록 사용자
	 * @param notificationId 읽음 처리할 알림 ID
	 * @return 읽음 상태가 확정된 본인 알림 ID
	 * @throws BusinessException 본인 알림을 찾을 수 없는 경우
	 */
	@Transactional
	public UUID markRead(User user, UUID notificationId) {
		UUID userId = user.id();
		if (repository.markRead(notificationId, userId) == 0
				&& !repository.belongsTo(notificationId, userId)) {
			throw new BusinessException(NotificationErrorCode.NOT_FOUND);
		}
		return notificationId;
	}

	/**
	 * 본인의 미읽음 알림 전체를 쓰기 트랜잭션에서 갱신한다.
	 *
	 * @param user 공통 사용자 처리에서 확인한 등록 사용자
	 * @return 실제 변경 건수. 이미 읽은 알림은 제외
	 */
	@Transactional
	public long markAllRead(User user) {
		return repository.markAllRead(user.id());
	}
}
