package com.getddo.db.common.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

/** 수정 시각이 필요한 Entity의 공통 기반. */
@Getter
@MappedSuperclass
public abstract class BaseUpdatableEntity extends BaseEntity {
	/** 마지막 수정 시각. 최초 저장 시 생성 시각과 같다. */
	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
