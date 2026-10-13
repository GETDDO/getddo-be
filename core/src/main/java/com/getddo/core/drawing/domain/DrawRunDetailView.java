package com.getddo.core.drawing.domain;

import java.util.List;
import lombok.Getter;

/** 하나의 조회 트랜잭션에서 읽은 실행과 결과다. */
@Getter
public final class DrawRunDetailView {
	private final DrawRunView run;
	private final List<DrawResultView> results;
	private final DrawExecutionSummary execution;
	/** 같은 조회 단위의 실행과 결과를 보존한다. 연결하지 않은 운영 요약은 null로 유지한다. */
	public DrawRunDetailView(DrawRunView run, List<DrawResultView> results) {
		this(run, results, null);
	}
	/** 같은 조회 단위의 실행과 결과를 보존한다. 연결하지 않은 운영 요약은 null로 유지한다. */
	public DrawRunDetailView(DrawRunView run, List<DrawResultView> results, DrawExecutionSummary execution) {
		this.execution = execution;
		this.run = run;
		this.results = List.copyOf(results);
	}
}
