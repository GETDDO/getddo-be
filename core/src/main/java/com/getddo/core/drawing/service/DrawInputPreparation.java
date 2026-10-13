package com.getddo.core.drawing.service;

import java.util.UUID;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawType;

/** 실행 종류별 입력 준비. GD-138은 REPLACEMENT 구현을 제공한다. */
public interface DrawInputPreparation {
	/** 이 준비 기능이 담당하는 실행 종류인지 판별해 공통 서비스가 정확히 하나의 구현을 선택하게 한다. */
	boolean supports(DrawType type);
	/** 이벤트 잠금과 시도 소유권을 보유한 호출자의 트랜잭션에서 동일 실행만 준비한다. */
	DrawSnapshot prepare(UUID runId, UUID attemptId);
}
