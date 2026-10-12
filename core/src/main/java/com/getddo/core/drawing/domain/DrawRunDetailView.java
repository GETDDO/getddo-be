package com.getddo.core.drawing.domain;

import java.util.List;
import lombok.Getter;

/** 하나의 조회 트랜잭션에서 읽은 실행과 결과다. */
@Getter
public final class DrawRunDetailView {
	private final DrawRunView run;
	private final List<DrawResultView> results;
	private final DrawExecutionSummary execution;
	public DrawRunDetailView(DrawRunView run, List<DrawResultView> results) {
		this(run, results, null);
	}
	public DrawRunDetailView(DrawRunView run, List<DrawResultView> results, DrawExecutionSummary execution) {
		this.execution = execution;
		this.run = run;
		this.results = List.copyOf(results);
	}
}
