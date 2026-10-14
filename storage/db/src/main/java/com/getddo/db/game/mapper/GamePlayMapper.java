package com.getddo.db.game.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.getddo.core.game.domain.GamePlay;
import com.getddo.db.game.entity.GamePlayEntity;

/** 발급 시각은 공통 Entity의 createdAt에서 복원한다. */
@Mapper(componentModel = "spring")
public interface GamePlayMapper {

	GamePlayEntity toEntity(GamePlay play);

	@Mapping(target = "startedAt", source = "createdAt")
	GamePlay toDomain(GamePlayEntity entity);
}
