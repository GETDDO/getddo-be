package com.getddo.core.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.Ticket;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistory;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.repository.TicketHistoryRepository;
import com.getddo.core.ticket.repository.TicketRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketExpiryServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-01T00:00:00.123456789Z");
	private static final Instant NOW_MICROS = Instant.parse("2026-10-01T00:00:00.123456Z");
	private static final Instant CREATED_AT = Instant.parse("2026-09-10T00:00:00Z");
	private static final Instant EXPIRED_BEFORE = Instant.parse("2026-09-30T15:00:00Z");
	private static final UUID USER_ID = UUID.randomUUID();

	@Mock
	private TicketRepository ticketRepository;
	@Mock
	private TicketHistoryRepository historyRepository;

	private TicketExpiryService service;

	@BeforeEach
	void setUp() {
		service = new TicketExpiryService(ticketRepository, historyRepository,
				new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	}

	private static Ticket dueTicket(TicketGrade grade, TicketStatus status, long version) {
		return new Ticket(UUID.randomUUID(), USER_ID, new GrantSource(GrantSourceType.MISSION, UUID.randomUUID()),
				grade, status, EXPIRED_BEFORE, version, CREATED_AT, CREATED_AT);
	}

	@Test
	@DisplayName("만료 시각이 지난 응모권을 만료됨으로 바꾸고 응모권마다 EXPIRE 이력을 남긴 뒤 처리 건수를 돌려준다")
	void expiresDueTicketsAndRecordsHistories() {
		// given
		Ticket available = dueTicket(TicketGrade.BRONZE, TicketStatus.AVAILABLE, 1);
		Ticket returned = dueTicket(TicketGrade.GOLD, TicketStatus.RETURNED, 3);
		when(ticketRepository.findDueForExpiryForUpdate(NOW_MICROS, 50)).thenReturn(List.of(available, returned));
		// when
		int expired = service.expireDue(50);
		// then
		assertThat(expired).isEqualTo(2);
		ArgumentCaptor<List<Ticket>> tickets = ArgumentCaptor.forClass(List.class);
		verify(ticketRepository).updateAll(tickets.capture());
		assertThat(tickets.getValue()).extracting(Ticket::getStatus).containsOnly(TicketStatus.EXPIRED);
		assertThat(tickets.getValue()).extracting(Ticket::getVersion).containsExactly(2L, 4L);
		assertThat(tickets.getValue()).extracting(Ticket::getUpdatedAt).containsOnly(NOW_MICROS);
		ArgumentCaptor<List<TicketHistory>> histories = ArgumentCaptor.forClass(List.class);
		verify(historyRepository).saveAll(histories.capture());
		assertThat(histories.getValue()).hasSize(2);
		assertThat(histories.getValue()).extracting(TicketHistory::getOperationType)
				.containsOnly(TicketOperationType.EXPIRE);
		assertThat(histories.getValue()).extracting(TicketHistory::getStatus).containsOnly(TicketStatus.EXPIRED);
		assertThat(histories.getValue()).extracting(TicketHistory::getTicketId)
				.containsExactly(available.getId(), returned.getId());
		assertThat(histories.getValue()).extracting(TicketHistory::getCreatedAt).containsOnly(NOW_MICROS);
	}

	@Test
	@DisplayName("만료할 응모권이 없으면 아무것도 저장하지 않고 0을 돌려준다")
	void doesNothingWhenNothingIsDue() {
		// given
		when(ticketRepository.findDueForExpiryForUpdate(NOW_MICROS, 10)).thenReturn(List.of());
		// when
		int expired = service.expireDue(10);
		// then
		assertThat(expired).isZero();
		verify(ticketRepository, never()).updateAll(any());
		verifyNoInteractions(historyRepository);
	}

	@Test
	@DisplayName("한 번에 처리할 최대 건수가 1 미만이면 저장소를 건드리지 않고 거절한다")
	void rejectsNonPositiveLimit() {
		// given
		// when
		// then
		assertThatThrownBy(() -> service.expireDue(0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.expireDue(-5)).isInstanceOf(IllegalArgumentException.class);
		verifyNoInteractions(ticketRepository, historyRepository);
	}
}
