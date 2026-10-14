package com.getddo.core.entry.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.entry.domain.Entry;
import com.getddo.core.entry.domain.EntryCommand;
import com.getddo.core.entry.domain.EntryEvent;
import com.getddo.core.entry.domain.EntryStatus;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.entry.repository.EntryEventReader;
import com.getddo.core.entry.repository.EntryRepository;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.user.domain.Membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryRejectionRecorderTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID EVENT_ID = UUID.randomUUID();
	private static final UUID ENTRY_ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-10-14T03:00:00.123456789Z");

	@Mock
	private EntryRepository entryRepository;
	@Mock
	private EntryEventReader eventReader;

	private EntryRejectionRecorder recorder() {
		return new EntryRejectionRecorder(entryRepository, eventReader,
				new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	}

	private static EntryCommand command() {
		return new EntryCommand(USER_ID, false, Membership.VIP, EVENT_ID, ENTRY_ID,
				Map.of(TicketGrade.SILVER, 2L, TicketGrade.GOLD, 0L));
	}

	private EntryEvent event(boolean deleted) {
		EntryEvent event = mock(EntryEvent.class);
		when(event.isDeleted()).thenReturn(deleted);
		return event;
	}

	@Test
	@DisplayName("저장 대상 거절은 응모자·차감 없이 요청 장수와 거절 코드를 마이크로초 시각으로 남긴다")
	void savesRecordableRejection() {
		// given
		when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.empty());
		EntryEvent live = event(false);
		when(eventReader.find(EVENT_ID)).thenReturn(Optional.of(live));
		// when
		recorder().record(command(), new EntryException(EntryErrorCode.TICKET_LIMIT_EXCEEDED));
		// then
		ArgumentCaptor<Entry> saved = ArgumentCaptor.forClass(Entry.class);
		verify(entryRepository).save(saved.capture());
		Entry entry = saved.getValue();
		assertThat(entry.getId()).isEqualTo(ENTRY_ID);
		assertThat(entry.getStatus()).isEqualTo(EntryStatus.REJECTED);
		assertThat(entry.getParticipantId()).isNull();
		assertThat(entry.getRequestedTicketsByGrade()).containsEntry(TicketGrade.SILVER, 2L)
				.containsEntry(TicketGrade.GOLD, 0L);
		assertThat(entry.getDeductedTicketCount()).isZero();
		assertThat(entry.getRejectionCode()).isEqualTo("ENTRY-007");
		assertThat(entry.getCreatedAt()).isEqualTo(Instant.parse("2026-10-14T03:00:00.123456Z"));
	}

	@Test
	@DisplayName("응모권 부족 거절도 TICKET-006으로 남긴다")
	void savesTicketInsufficientRejection() {
		// given
		when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.empty());
		EntryEvent live = event(false);
		when(eventReader.find(EVENT_ID)).thenReturn(Optional.of(live));
		// when
		recorder().record(command(), new TicketException(TicketErrorCode.TICKET_INSUFFICIENT));
		// then
		ArgumentCaptor<Entry> saved = ArgumentCaptor.forClass(Entry.class);
		verify(entryRepository).save(saved.capture());
		assertThat(saved.getValue().getRejectionCode()).isEqualTo("TICKET-006");
	}

	@Test
	@DisplayName("저장 대상이 아닌 거절(요청 형식, 없는 이벤트, 같은 키의 다른 요청)은 남기지 않는다")
	void ignoresNonRecordableFailures() {
		// given
		EntryRejectionRecorder recorder = recorder();
		// when
		recorder.record(command(), new EntryException(EntryErrorCode.INVALID_ENTRY_REQUEST));
		recorder.record(command(), new EntryException(EntryErrorCode.EVENT_NOT_FOUND));
		recorder.record(command(), new EntryException(EntryErrorCode.IDEMPOTENCY_CONFLICT));
		// then
		verify(entryRepository, never()).save(any());
	}

	@Test
	@DisplayName("장수 합계가 int 범위를 넘는 요청은 기간·관리자 거절이어도 이력으로 남기지 않는다")
	void skipsRequestsWhoseTotalDoesNotFitInt() {
		// given
		when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.empty());
		EntryEvent live = event(false);
		when(eventReader.find(EVENT_ID)).thenReturn(Optional.of(live));
		EntryCommand huge = new EntryCommand(USER_ID, true, null, EVENT_ID, ENTRY_ID,
				Map.of(TicketGrade.BRONZE, (long) Integer.MAX_VALUE, TicketGrade.SILVER, 1L));
		EntryCommand overflowing = new EntryCommand(USER_ID, true, null, EVENT_ID, ENTRY_ID,
				Map.of(TicketGrade.BRONZE, Long.MAX_VALUE, TicketGrade.SILVER, Long.MAX_VALUE, TicketGrade.GOLD, 3L));
		EntryRejectionRecorder recorder = recorder();
		// when
		recorder.record(huge, new EntryException(EntryErrorCode.ADMIN_ENTRY_FORBIDDEN));
		recorder.record(overflowing, new EntryException(EntryErrorCode.ADMIN_ENTRY_FORBIDDEN));
		// then
		verify(entryRepository, never()).save(any());
	}

	@Test
	@DisplayName("같은 응모 ID의 결과가 이미 있으면 다시 남기지 않는다")
	void skipsWhenResultAlreadyStored() {
		// given
		when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.of(mock(Entry.class)));
		// when
		recorder().record(command(), new EntryException(EntryErrorCode.EVENT_NOT_OPEN));
		// then
		verify(entryRepository, never()).save(any());
	}

	@Test
	@DisplayName("없거나 삭제된 이벤트에는 이력을 남기지 않는다")
	void skipsWhenEventMissingOrDeleted() {
		// given
		when(entryRepository.findById(ENTRY_ID)).thenReturn(Optional.empty());
		EntryEvent deleted = event(true);
		when(eventReader.find(EVENT_ID)).thenReturn(Optional.empty()).thenReturn(Optional.of(deleted));
		EntryRejectionRecorder recorder = recorder();
		// when
		recorder.record(command(), new EntryException(EntryErrorCode.ADMIN_ENTRY_FORBIDDEN));
		recorder.record(command(), new EntryException(EntryErrorCode.ADMIN_ENTRY_FORBIDDEN));
		// then
		verify(entryRepository, never()).save(any());
	}
}
