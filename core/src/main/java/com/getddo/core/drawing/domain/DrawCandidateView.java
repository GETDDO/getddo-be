package com.getddo.core.drawing.domain;

import java.util.UUID;

import lombok.Getter;

/** 저장된 추첨 자료를 표현하는 불변 조회 값이다. */
@Getter
public final class DrawCandidateView {
	private final DrawSnapshot.Candidate snapshot;
	private final UUID sourceRunId;
	private final UUID sourceEventId;
	private final UUID participantEventId;
	private final UUID participantUserId;

	public DrawCandidateView(DrawSnapshot.Candidate snapshot, UUID sourceRunId, UUID sourceEventId, UUID participantEventId, UUID participantUserId) {
		this.snapshot = snapshot;
		this.sourceRunId = sourceRunId;
		this.sourceEventId = sourceEventId;
		this.participantEventId = participantEventId;
		this.participantUserId = participantUserId;
	}
}
