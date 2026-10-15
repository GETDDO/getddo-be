package com.getddo.core.event.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.repository.EventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventLifecycleServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
	private final EventRepository events = mock(EventRepository.class);
	private final EventLifecycleTransitionService transitions = mock(EventLifecycleTransitionService.class);
	private final EventLifecycleService service = new EventLifecycleService(events,
			new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)), transitions);

	@Test
	@DisplayName("개별 실패 후 다음 후보를 처리하며 다음 폴링은 실패한 묶음 뒤부터 순환 조회한다")
	void continuesAndRotatesAfterFailure() {
		// given
		UUID failed = UUID.randomUUID();
		UUID next = UUID.randomUUID();
		when(events.findLifecycleDueIds(NOW, null, 1)).thenReturn(List.of(failed));
		when(events.findLifecycleDueIds(NOW, failed, 1)).thenReturn(List.of(next));
		when(transitions.advance(failed, NOW)).thenThrow(new IllegalStateException("감사 실패"));
		when(transitions.advance(next, NOW)).thenReturn(true);
		// when / then
		assertThat(service.advanceDue(1)).isZero();
		assertThat(service.advanceDue(1)).isEqualTo(1);
		verify(transitions).advance(next, NOW);
	}

	@Test
	@DisplayName("잘못된 묶음 크기는 저장소와 개별 처리를 호출하지 않고 거절한다")
	void rejectsInvalidBatchSize() {
		// given / when / then
		assertThatThrownBy(() -> service.advanceDue(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.advanceDue(101)).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(events, transitions);
	}
}
