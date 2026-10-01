package com.getddo.db.ticket.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.getddo.core.ticket.domain.TicketWallet;
import com.getddo.db.ticket.entity.TicketWalletEntity;

/** 지갑 Entity를 도메인 객체로 변환한다. 지갑 행은 네이티브 INSERT로만 만들어 Entity로의 변환은 없다. */
@Mapper(componentModel = "spring")
public interface TicketWalletMapper {

	/** {@code deposit(long)}은 새 지갑을 돌려주는 업무 메서드라 속성이 아니다. */
	@Mapping(target = "deposit", ignore = true)
	TicketWallet toDomain(TicketWalletEntity entity);
}
