package com.getddo.core.drawing.domain;

import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** 같은 트랜잭션에서 확인한 관리자 확인 기록과 실제 최신 공개 버전이다. 공개 전에는 publication이 null이다. */
@Getter
@RequiredArgsConstructor
@Accessors(fluent = true)
public final class DrawPublicationConfirmation {
	private final UUID eventId;
	private final DrawResultConfirmation confirmation;
	private final DrawPublication publication;
}
