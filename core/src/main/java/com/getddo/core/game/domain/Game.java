package com.getddo.core.game.domain;

import java.util.Objects;
import java.util.UUID;

import lombok.Getter;

/** 게임 목록과 플레이 발급에 사용하는 게임 설정. */
@Getter
public final class Game {

	private final UUID id;
	private final String code;
	private final String name;
	private final String description;
	private final String rules;
	private final String ruleVersion;
	private final boolean active;

	public Game(UUID id, String code, String name, String description, String rules, String ruleVersion,
			boolean active) {
		this.id = id;
		this.code = Objects.requireNonNull(code, "code");
		this.name = Objects.requireNonNull(name, "name");
		this.description = description;
		this.rules = Objects.requireNonNull(rules, "rules");
		this.ruleVersion = Objects.requireNonNull(ruleVersion, "ruleVersion");
		if (code.isBlank() || code.codePointCount(0, code.length()) > 50) {
			throw new IllegalArgumentException("code는 공백이 아닌 1~50자여야 합니다.");
		}
		if (name.isBlank() || name.codePointCount(0, name.length()) > 100) {
			throw new IllegalArgumentException("name은 공백이 아닌 1~100자여야 합니다.");
		}
		if (ruleVersion.isBlank() || ruleVersion.codePointCount(0, ruleVersion.length()) > 30) {
			throw new IllegalArgumentException("ruleVersion은 공백이 아닌 1~30자여야 합니다.");
		}
		this.active = active;
	}

	public static Game create(String code, String name, String description, String rules, String ruleVersion) {
		return new Game(null, code, name, description, rules, ruleVersion, true);
	}
}
