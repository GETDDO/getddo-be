package com.getddo.core.entry.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.entry.domain.EntryEvent;
import com.getddo.core.entry.domain.EntryParticipant;
import com.getddo.core.entry.domain.EntryParticipationTotals;
import com.getddo.core.entry.domain.EntryStatistics;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.entry.repository.EntryEventReader;
import com.getddo.core.entry.repository.EntryParticipantRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryStatisticsServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID EVENT_ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-10-14T03:00:00Z");

	@Mock
	private EntryEventReader eventReader;
	@Mock
	private EntryParticipantRepository participantRepository;

	private EntryStatisticsService service() {
		return new EntryStatisticsService(eventReader, participantRepository,
				new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	}

	private static EntryEvent event(boolean deleted) {
		EntryEvent event = mock(EntryEvent.class);
		when(event.isDeleted()).thenReturn(deleted);
		return event;
	}

	@Test
	@DisplayName("이벤트 합계와 본인 차감 수량, 서버 시각을 함께 돌려준다")
	void returnsTotalsAndMyCount() {
		// given
		EntryEvent live = event(false);
		when(eventReader.find(EVENT_ID)).thenReturn(Optional.of(live));
		when(participantRepository.summarize(EVENT_ID)).thenReturn(new EntryParticipationTotals(3, 11));
		when(participantRepository.find(EVENT_ID, USER_ID))
				.thenReturn(Optional.of(new EntryParticipant(UUID.randomUUID(), EVENT_ID, USER_ID, 4, NOW)));
		// when
		EntryStatistics statistics = service().getStatistics(USER_ID, EVENT_ID);
		// then
		assertThat(statistics.getEventId()).isEqualTo(EVENT_ID);
		assertThat(statistics.getParticipantCount()).isEqualTo(3);
		assertThat(statistics.getTotalSpentTicketCount()).isEqualTo(11);
		assertThat(statistics.getMySpentTicketCount()).isEqualTo(4);
		assertThat(statistics.getServerTime()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("응모하지 않은 사용자의 본인 차감 수량은 0이다")
	void myCountIsZeroWithoutParticipation() {
		// given
		EntryEvent live = event(false);
		when(eventReader.find(EVENT_ID)).thenReturn(Optional.of(live));
		when(participantRepository.summarize(EVENT_ID)).thenReturn(new EntryParticipationTotals(0, 0));
		when(participantRepository.find(EVENT_ID, USER_ID)).thenReturn(Optional.empty());
		// when
		EntryStatistics statistics = service().getStatistics(USER_ID, EVENT_ID);
		// then
		assertThat(statistics.getParticipantCount()).isZero();
		assertThat(statistics.getTotalSpentTicketCount()).isZero();
		assertThat(statistics.getMySpentTicketCount()).isZero();
	}

	@Test
	@DisplayName("없거나 삭제된 이벤트는 ENTRY-004이고 합계를 읽지 않는다")
	void missingOrDeletedEventIsNotFound() {
		// given
		EntryEvent deleted = event(true);
		when(eventReader.find(EVENT_ID)).thenReturn(Optional.empty()).thenReturn(Optional.of(deleted));
		EntryStatisticsService service = service();
		// when
		// then
		for (int i = 0; i < 2; i++) {
			assertThatThrownBy(() -> service.getStatistics(USER_ID, EVENT_ID))
					.isInstanceOfSatisfying(EntryException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(EntryErrorCode.EVENT_NOT_FOUND));
		}
		verifyNoInteractions(participantRepository);
	}

	@Test
	@DisplayName("본인 차감 수량이 전체 합계보다 크면 어긋난 데이터로 보고 만들지 않는다")
	void rejectsInconsistentTotals() {
		// given
		UUID eventId = UUID.randomUUID();
		// when
		// then
		assertThatThrownBy(() -> new EntryStatistics(eventId, 1, 2, 3, NOW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new EntryStatistics(eventId, -1, 0, 0, NOW))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new EntryParticipationTotals(0, -1)).isInstanceOf(IllegalArgumentException.class);
	}
}
