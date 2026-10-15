package com.getddo.core.entry.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.entry.domain.EntryHistoryQuery;
import com.getddo.core.entry.domain.EntrySummary;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.entry.repository.EntryEventReader;
import com.getddo.core.entry.repository.EntryRepository;

/** 내 응모 이력 조회(E05·E06). 접수 건과 거절 건을 함께 돌려준다. */
@Service
@RequiredArgsConstructor
public class EntryHistoryService {

	private final EntryRepository entryRepository;
	private final EntryEventReader eventReader;

	/**
	 * 한 이벤트에 대한 내 응모 이력을 조회한다(E05).
	 *
	 * @throws EntryException 이벤트가 없는 경우({@code ENTRY-004})
	 */
	@Transactional(readOnly = true)
	public PageResult<EntrySummary> findMyEntriesOfEvent(UUID userId, UUID eventId, PageQuery page) {
		// 삭제된 이벤트의 이력도 볼 수 있어야 하므로 삭제 여부는 보지 않는다.
		eventReader.find(eventId).orElseThrow(() -> new EntryException(EntryErrorCode.EVENT_NOT_FOUND));
		return entryRepository.findByUser(userId, EntryHistoryQuery.ofEvent(eventId), page);
	}

	/** 내 모든 응모 이력을 조건에 맞게 조회한다(E06). 조건에 맞는 이력이 없으면 빈 목록이다. */
	@Transactional(readOnly = true)
	public PageResult<EntrySummary> findMyEntries(UUID userId, EntryHistoryQuery query, PageQuery page) {
		return entryRepository.findByUser(userId, query, page);
	}
}
