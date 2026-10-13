package com.getddo.db.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.getddo.db.common.entity.BaseUpdatableEntity;

/** 게임 코드·규칙·활성 상태를 보관하는 저장 모델. */
@Getter
@Entity
@Table(name = "games")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GameEntity extends BaseUpdatableEntity {

	@Column(name = "code", nullable = false, length = 50)
	private String code;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", columnDefinition = "text")
	private String description;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "rules", nullable = false, columnDefinition = "json")
	private String rules;

	@Column(name = "rule_version", nullable = false, length = 30)
	private String ruleVersion;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Builder
	private GameEntity(String code,
			String name,
			String description,
			String rules,
			String ruleVersion,
			boolean active) {
		this.code = code;
		this.name = name;
		this.description = description;
		this.rules = rules;
		this.ruleVersion = ruleVersion;
		this.active = active;
	}
}
