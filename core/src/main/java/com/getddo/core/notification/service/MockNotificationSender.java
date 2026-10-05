package com.getddo.core.notification.service;

import org.springframework.stereotype.Service;

import com.getddo.core.notification.domain.MockDeliveryStatus;
import com.getddo.core.notification.domain.NotificationDelivery;

/** 외부 문자·메일·푸시 연결 없이 발송 성공을 모의 처리한다. 장애 검증에서는 이 Bean을 대체한다. */
@Service
public class MockNotificationSender {
	/** 외부 전송 없이 모의 발송 성공을 반환한다. 실패 경로 검증은 테스트에서 이 Bean을 대체한다. */
	public MockDeliveryStatus send(NotificationDelivery delivery) {
		return MockDeliveryStatus.SENT;
	}
}
