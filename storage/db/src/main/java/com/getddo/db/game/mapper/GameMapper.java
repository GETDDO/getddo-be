package com.getddo.db.game.mapper;

import org.mapstruct.Mapper;

import com.getddo.core.game.domain.Game;
import com.getddo.db.game.entity.GameEntity;

/** 게임 도메인과 저장 모델을 변환한다. ID와 감사 시각은 공통 Entity가 발급한다. */
@Mapper(componentModel = "spring")
public interface GameMapper {

	GameEntity toEntity(Game game);

	Game toDomain(GameEntity entity);
}
