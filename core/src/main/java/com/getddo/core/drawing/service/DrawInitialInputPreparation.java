package com.getddo.core.drawing.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.getddo.core.drawing.domain.DrawSnapshot;
import com.getddo.core.drawing.domain.DrawType;

/** 공통 실행이 등록된 최초 실행의 입력을 준비할 때 사용하는 어댑터다. */
@Service
@RequiredArgsConstructor
public class DrawInitialInputPreparation implements DrawInputPreparation {
	private final DrawSnapshotService snapshots;
	/** 최초 추첨의 등록 입력 준비만 담당하므로 INITIAL 종류를 지원한다. */
	@Override public boolean supports(DrawType type) { return type == DrawType.INITIAL; }
	/** 공통 서비스가 확인한 소유권·트랜잭션 안에서 등록된 최초 실행의 입력 준비에 위임한다. */
	@Override public DrawSnapshot prepare(UUID runId, UUID attemptId) { return snapshots.prepareRegistered(runId); }
}
