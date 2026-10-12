package com.getddo.db.entry.mapper;

import org.mapstruct.Mapper;

import com.getddo.core.entry.domain.Entry;
import com.getddo.core.entry.domain.EntryParticipant;
import com.getddo.db.entry.entity.EventEntryEntity;
import com.getddo.db.entry.entity.EventParticipantEntity;

/**
 * 응모자·응모 Entity와 도메인 객체 변환.
 *
 * <p>응모자 ID는 저장할 때 발급되므로 Entity로 옮기지 않는다. 응모 Entity에는 이벤트 ID가 없어(응모자를 거쳐 안다) Entity에서
 * 도메인으로의 변환은 저장소가 응모자를 조회해 직접 만든다.</p>
 */
@Mapper(componentModel = "spring")
public interface EntryMapper {

	EntryParticipant toDomain(EventParticipantEntity entity);

	EventParticipantEntity toEntity(EntryParticipant participant);

	EventEntryEntity toEntity(Entry entry);
}
