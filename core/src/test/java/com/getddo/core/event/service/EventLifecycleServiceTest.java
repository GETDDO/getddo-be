package com.getddo.core.event.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.getddo.core.audit.service.AuditLogService;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.domain.EventLifecycleState;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.repository.EventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventLifecycleServiceTest {
	private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
	private final EventRepository events = mock(EventRepository.class);
	private final AuditLogService audits = mock(AuditLogService.class);
	private final EventLifecycleService service = new EventLifecycleService(events,
			new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)), audits);

	@ParameterizedTest
	@CsvSource({"SCHEDULED,0,60,OPEN", "SCHEDULED,-60,0,CLOSED", "OPEN,-60,0,CLOSED",
			"SCHEDULED,1,60,SCHEDULED", "CANCELED,-60,0,CANCELED", "PUBLISHED,-60,0,PUBLISHED"})
	@DisplayName("시작·마감 경계와 장애 후 복구를 적용하며 취소·발표 상태는 되돌리지 않는다")
	void transitionsOnlyRecruitmentStatuses(EventStatus status, long startOffset, long endOffset, EventStatus expected) {
		// given
		UUID id = UUID.randomUUID();
		when(events.findLifecycleDueIdsForUpdate(NOW, 20)).thenReturn(List.of(id));
		when(events.findLifecycleByIdForUpdate(id)).thenReturn(Optional.of(
				new EventLifecycleState(status, NOW.plusSeconds(startOffset), NOW.plusSeconds(endOffset))));
		// when
		int changed = service.advanceDue(20);
		// then
		if (status == expected) {
			assertThat(changed).isZero();
			verify(events, never()).updateStatus(any(), any());
			verifyNoInteractions(audits);
		} else {
			assertThat(changed).isEqualTo(1);
			verify(events).updateStatus(id, expected);
			verify(audits).record(any());
		}
	}

	@Test
	@DisplayName("잘못된 묶음 크기는 저장소를 호출하지 않고 거절한다")
	void rejectsInvalidBatchSize() {
		// given / when / then
		assertThatThrownBy(() -> service.advanceDue(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.advanceDue(101)).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(events, audits);
	}
}
