package com.getddo.core.game.domain;

import java.util.Objects;

import lombok.Getter;

/** 게임별로 검증한 시작 규칙과 저장할 문제 생성 버전. */
@Getter
public final class GameStartConfiguration {

	private final GameStartRules rules;
	private final String generatorVersion;

	public GameStartConfiguration(GameStartRules rules, String generatorVersion) {
		this.rules = Objects.requireNonNull(rules, "rules");
		this.generatorVersion = Objects.requireNonNull(generatorVersion, "generatorVersion");
	}
}
