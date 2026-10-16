package com.getddo.db.game.mapper;

import org.mapstruct.Mapper;

import com.getddo.core.game.domain.GameRewardClaim;
import com.getddo.core.game.domain.GameStatistics;
import com.getddo.db.game.entity.GameRewardClaimEntity;
import com.getddo.db.game.entity.UserGameStatsEntity;

/** 게임 청구・집계의 단순 필드 변환. 정책 판정과 DB 조회는 하지 않는다. */
@Mapper(componentModel = "spring")
public interface GameResultMapper {
	GameRewardClaimEntity toEntity(GameRewardClaim claim);
	GameRewardClaim toDomain(GameRewardClaimEntity entity);
	GameStatistics toDomain(UserGameStatsEntity entity);
}
