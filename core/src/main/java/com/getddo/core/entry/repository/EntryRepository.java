package com.getddo.core.entry.repository;

import java.util.Optional;
import java.util.UUID;

import com.getddo.core.entry.domain.Entry;

/** 접수 완료된 응모 저장소. 응모는 추가만 하며 수정·삭제하지 않는다. */
public interface EntryRepository {

	Optional<Entry> findById(UUID id);

	/**
	 * 접수 완료된 응모를 저장하고 즉시 DB에 반영한다. 차감 이력이 응모 행을 참조하므로 차감 전에 반영해야 한다.
	 * 같은 ID의 응모가 이미 있으면 PK 위반 예외가 난다.
	 */
	Entry save(Entry entry);
}
