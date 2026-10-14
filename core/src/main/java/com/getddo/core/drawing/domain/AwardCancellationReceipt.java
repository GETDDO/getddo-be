package com.getddo.core.drawing.domain;

/** 등록 취소와 실제 공통 실행이 반환한 현재 상태를 함께 전달한다. */
public record AwardCancellationReceipt(AwardCancellation cancellation, DrawExecutionResult execution) {
}
