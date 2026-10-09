package com.getddo.core.notification.domain;

/** 외부 발송 대신 기록하는 모의 발송 결과다. 읽음 여부를 나타내지 않는다. */
public enum MockDeliveryStatus {
	PENDING,
	SENT,
	FAILED
}
