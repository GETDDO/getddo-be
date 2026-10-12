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
		this.active = active;
	}

	public static Game create(String code, String name, String description, String rules, String ruleVersion) {
		return new Game(null, code, name, description, rules, ruleVersion, true);
	}
}
