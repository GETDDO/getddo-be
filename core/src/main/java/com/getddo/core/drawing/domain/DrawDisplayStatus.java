package com.getddo.core.drawing.domain;

/** 추첨 내부 처리 과정 대신 사용자에게 보여 줄 상태다. */
public enum DrawDisplayStatus {
	/** 실제 공개 기록이 없어 결과 명단을 숨기는 상태다. */
	WAITING,
	/** 실제 공개 버전의 명단을 제공할 수 있는 상태다. */
	PUBLISHED,
	/** 이벤트가 취소된 상태로, 과거 공개 이력의 존재 여부와는 별개다. */
	CANCELED,
	/** 최초 추첨이 응모자 없이 종료되어 결과 대기와 구분하는 상태다. */
	NO_ENTRANTS
}
