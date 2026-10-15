package com.getddo.core.drawing.domain;

/** 본인 응모와 실제 공개 버전에서 판단한 결과다. */
public enum MyDrawResultStatus {
	/** 접수된 본인 응모가 없으며, 이벤트 취소 여부보다 먼저 판단한다. */
	NOT_ENTERED,
	/** 본인 응모는 접수됐지만 실제 공개 기록이 없어 당첨 여부를 숨긴다. */
	PENDING,
	/** 최신 공개 버전에 본인 당첨 결과가 포함돼 있다. */
	WON,
	/** 응모했지만 최신 공개 버전에 본인 당첨 결과가 없다. */
	LOST,
	/** 본인이 응모한 이벤트가 취소돼 경품을 제공하지 않는다. */
	CANCELED
}
