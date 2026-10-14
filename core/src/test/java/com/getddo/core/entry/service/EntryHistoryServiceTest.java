package com.getddo.core.entry.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.entry.domain.EntryEvent;
import com.getddo.core.entry.domain.EntryHistoryQuery;
import com.getddo.core.entry.domain.EntryStatus;
import com.getddo.core.entry.domain.EntrySummary;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.entry.repository.EntryEventReader;
import com.getddo.core.entry.repository.EntryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryHistoryServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID EVENT_ID = UUID.randomUUID();
	private static final PageQuery FIRST_PAGE = new PageQuery(1, 20);

	@Mock
	private EntryRepository entryRepository;
	@Mock
	private EntryEventReader eventReader;

	@Test
	@DisplayName("이벤트별 조회는 그 이벤트만 조건으로 저장소에 넘기고 결과를 그대로 돌려준다")
	void findsEntriesOfEvent() {
		// given
		PageResult<EntrySummary> page = new PageResult<>(List.of(), 1, 20, 0);
		when(eventReader.find(EVENT_ID)).thenReturn(Optional.of(mock(EntryEvent.class)));
		when(entryRepository.findByUser(any(), any(), any())).thenReturn(page);
		// when
		PageResult<EntrySummary> result = new EntryHistoryService(entryRepository, eventReader)
				.findMyEntriesOfEvent(USER_ID, EVENT_ID, FIRST_PAGE);
		// then
		ArgumentCaptor<EntryHistoryQuery> query = ArgumentCaptor.forClass(EntryHistoryQuery.class);
		verify(entryRepository).findByUser(org.mockito.ArgumentMatchers.eq(USER_ID), query.capture(),
				org.mockito.ArgumentMatchers.eq(FIRST_PAGE));
		assertThat(result).isSameAs(page);
		assertThat(query.getValue().getEventId()).isEqualTo(EVENT_ID);
		assertThat(query.getValue().getStatus()).isNull();
	}

	@Test
	@DisplayName("없는 이벤트의 이벤트별 조회는 ENTRY-004이고 저장소를 조회하지 않는다")
	void missingEventIsNotFound() {
		// given
		when(eventReader.find(EVENT_ID)).thenReturn(Optional.empty());
		// when
		// then
		assertThatThrownBy(() -> new EntryHistoryService(entryRepository, eventReader)
				.findMyEntriesOfEvent(USER_ID, EVENT_ID, FIRST_PAGE))
				.isInstanceOfSatisfying(EntryException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(EntryErrorCode.EVENT_NOT_FOUND));
		verify(entryRepository, never()).findByUser(any(), any(), any());
	}

	@Test
	@DisplayName("전체 조회는 조건을 그대로 저장소에 넘긴다")
	void findsAllEntriesWithQuery() {
		// given
		EntryHistoryQuery query = new EntryHistoryQuery(EVENT_ID, EntryStatus.REJECTED, null, null);
		PageResult<EntrySummary> page = new PageResult<>(List.of(), 1, 20, 0);
		when(entryRepository.findByUser(USER_ID, query, FIRST_PAGE)).thenReturn(page);
		// when
		PageResult<EntrySummary> result = new EntryHistoryService(entryRepository, eventReader)
				.findMyEntries(USER_ID, query, FIRST_PAGE);
		// then
		assertThat(result).isSameAs(page);
	}
}
