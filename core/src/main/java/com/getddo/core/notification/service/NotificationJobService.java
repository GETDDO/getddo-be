package com.getddo.core.notification.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.notification.domain.MockDeliveryStatus;
import com.getddo.core.notification.domain.NotificationDelivery;
import com.getddo.core.notification.domain.NotificationJob;
import com.getddo.core.notification.domain.NotificationJobRequest;
import com.getddo.core.notification.domain.NotificationRetryPolicy;
import com.getddo.core.notification.exception.NotificationProcessingErrorCode;
import com.getddo.core.notification.exception.NotificationProcessingException;
import com.getddo.core.notification.repository.NotificationJobRepository;

/**
 * DB에 저장된 알림 작업을 처리하고 생성·모의 발송의 재시도를 분리한다.
 *
 * <p>등록은 원본 업무 트랜잭션에 참여하므로 롤백된 발생 사실의 알림은 남지 않는다.
 * 처리는 저장소의 별도 트랜잭션들로 진행하며 이미 확정된 원본 결과를 변경하지 않는다.
 * 사용자별 저장 중 실패해도 앞서 저장한 알림은 유지된다.</p>
 */
@Service
@RequiredArgsConstructor
public class NotificationJobService {
	private final NotificationJobRepository repository;
	private final TimeProvider timeProvider;
	private final MockNotificationSender sender;

	/** 동일 발생 키는 동일 입력에만 재사용한다. 서로 다른 업무 유형도 키가 충돌하지 않아야 한다. */
	@Transactional
	public UUID register(NotificationJobRequest request) {
		if (request == null) {
			throw new NotificationProcessingException(NotificationProcessingErrorCode.INVALID_JOB);
		}
		return repository.register(request, timeProvider.now());
	}

	/** 작업 하나를 선점·처리한다. 대상 작업이 없으면 false, 선점한 경우 true다. */
	public boolean processNextJob() {
		NotificationJob job = repository.claimNextJob(timeProvider.now()).orElse(null);
		if (job == null) {
			return false;
		}
		try {
			NotificationJobRequest request = repository.findRequest(job.getId());
			for (UUID userId : request.getRecipientIds()) {
				if (!repository.createNotification(job, request, userId, timeProvider.now())) {
					return true;
				}
			}
			repository.completeJob(job, timeProvider.now());
		} catch (RuntimeException failure) {
			repository.failJob(job, NotificationRetryPolicy.nextAttemptAt(
					job.getAttemptCount(), timeProvider.now(), failure), NotificationRetryPolicy.errorCode(failure));
		}
		return true;
	}

	/** 기존 알림 하나만 모의 발송한다. 재시도로 새 알림을 생성하거나 읽음 처리하지 않는다. */
	public boolean processNextDelivery() {
		NotificationDelivery delivery = repository.claimNextDelivery(timeProvider.now()).orElse(null);
		if (delivery == null) {
			return false;
		}
		try {
			if (sender.send(delivery) != MockDeliveryStatus.SENT) {
				throw new NotificationProcessingException(NotificationProcessingErrorCode.TEMPORARY_FAILURE);
			}
			repository.completeDelivery(delivery, timeProvider.now());
		} catch (RuntimeException failure) {
			repository.failDelivery(delivery, NotificationRetryPolicy.nextAttemptAt(
					delivery.getAttemptCount(), timeProvider.now(), failure), NotificationRetryPolicy.errorCode(failure));
		}
		return true;
	}
}
