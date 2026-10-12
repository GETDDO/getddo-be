package com.getddo.core.drawing.domain;

import java.util.List;
import lombok.Getter;

/** 하나의 조회 트랜잭션에서 읽은 실행과 후보·결과다. */
@Getter
public final class DrawRunDetailView {
	private final DrawRunView run;
	private final List<DrawCandidateView> candidates;
	private final List<DrawResultView> results;
	public DrawRunDetailView(DrawRunView run, List<DrawCandidateView> candidates, List<DrawResultView> results) {
		this.run = run;
		this.candidates = List.copyOf(candidates);
		this.results = List.copyOf(results);
	}
}
