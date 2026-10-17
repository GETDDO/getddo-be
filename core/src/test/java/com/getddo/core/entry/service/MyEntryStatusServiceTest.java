package com.getddo.core.entry.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.entry.repository.EnteredEventReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyEntryStatusServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID EVENT_A = UUID.randomUUID();
	private static final UUID EVENT_B = UUID.randomUUID();

	@Mock
	private EnteredEventReader reader;

	@Test
	@DisplayName("목록의 이벤트를 한 번에 저장소에 넘겨 응모한 이벤트의 ID를 돌려준다")
	void returnsEnteredEventIds() {
		// given
		List<UUID> eventIds = List.of(EVENT_A, EVENT_B);
		when(reader.findEnteredEventIds(USER_ID, eventIds)).thenReturn(Set.of(EVENT_B));
		// when
		Set<UUID> entered = new MyEntryStatusService(reader).findEnteredEventIds(USER_ID, eventIds);
		// then
		assertThat(entered).containsExactly(EVENT_B);
		verify(reader).findEnteredEventIds(USER_ID, eventIds);
	}

	@Test
	@DisplayName("비로그인이거나 이벤트 목록이 비면 저장소를 읽지 않고 빈 집합이다")
	void skipsLookupWithoutUserOrEvents() {
		MyEntryStatusService service = new MyEntryStatusService(reader);
		assertThat(service.findEnteredEventIds(null, List.of(EVENT_A))).isEmpty();
		assertThat(service.findEnteredEventIds(USER_ID, List.of())).isEmpty();
		assertThat(service.findEnteredEventIds(USER_ID, null)).isEmpty();
		verifyNoInteractions(reader);
	}
}
