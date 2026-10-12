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
	@Override public boolean supports(DrawType type) { return type == DrawType.INITIAL; }
	@Override public DrawSnapshot prepare(UUID runId, UUID attemptId) { return snapshots.prepareRegistered(runId); }
}
