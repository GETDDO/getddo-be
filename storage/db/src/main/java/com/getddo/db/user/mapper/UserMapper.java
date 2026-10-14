package com.getddo.db.user.mapper;

import org.mapstruct.Mapper;

import com.getddo.core.user.domain.User;
import com.getddo.db.user.entity.UserEntity;

@Mapper(componentModel = "spring")
public interface UserMapper {

	User toDomain(UserEntity entity);
}
