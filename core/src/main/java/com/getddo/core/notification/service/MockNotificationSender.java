package com.getddo.core.notification.service;

import org.springframework.stereotype.Service;

import com.getddo.core.notification.domain.MockDeliveryStatus;
import com.getddo.core.notification.domain.NotificationDelivery;

/** 외부 문자·메일·푸시 연결 없이 발송 성공을 모의 처리한다. 장애 검증에서는 이 Bean을 대체한다. */
@Service
public class MockNotificationSender {
	public MockDeliveryStatus send(NotificationDelivery delivery) {
		return MockDeliveryStatus.SENT;
	}
}
