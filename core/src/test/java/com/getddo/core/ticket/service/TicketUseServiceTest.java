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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
import com.getddo.core.ticket.domain.UseCommand;
import com.getddo.core.ticket.domain.UseResult;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.TicketHistoryRepository;
import com.getddo.core.ticket.repository.TicketRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketUseServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-15T03:00:00.123456789Z");
	private static final Instant NOW_MICROS = Instant.parse("2026-09-15T03:00:00.123456Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-09-30T15:00:00Z");
	private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID ENTRY_ID = UUID.randomUUID();

	@Mock
	private TicketRepository ticketRepository;
	@Mock
	private TicketHistoryRepository historyRepository;

	private TicketUseService service;

	@BeforeEach
	void setUp() {
		service = new TicketUseService(ticketRepository, historyRepository,
				new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	}

	private static Ticket ticket(TicketGrade grade) {
		return new Ticket(UUID.randomUUID(), USER_ID, new GrantSource(GrantSourceType.MISSION, UUID.randomUUID()),
				grade, TicketStatus.AVAILABLE, EXPIRES_AT, 1, CREATED_AT, CREATED_AT);
	}

	private static UseCommand command(long quantity) {
		return new UseCommand(USER_ID, ENTRY_ID, quantity, "이벤트 응모");
	}

	@Test
	@DisplayName("후보를 순서대로 사용 처리해 저장하고 마이크로초로 자른 시각을 응모권과 이력에 쓴다")
	void usesCandidatesInGivenOrder() {
		// given
		Ticket first = ticket(TicketGrade.BRONZE);
		Ticket second = ticket(TicketGrade.SILVER);
		when(ticketRepository.findUsableForUpdate(USER_ID, NOW_MICROS, 2)).thenReturn(List.of(first, second));
		// when
		UseResult result = service.use(command(2));
		// then
		assertThat(result.isReplayed()).isFalse();
		assertThat(result.getUsedAt()).isEqualTo(NOW_MICROS);
		assertThat(result.getTickets()).extracting(ticket -> ticket.getTicketId())
				.containsExactly(first.getId(), second.getId());
		assertThat(result.countByGrade()).containsEntry(TicketGrade.BRONZE, 1L)
				.containsEntry(TicketGrade.SILVER, 1L).containsEntry(TicketGrade.GOLD, 0L);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Ticket>> updated = ArgumentCaptor.forClass(List.class);
		verify(ticketRepository).updateAll(updated.capture());
		assertThat(updated.getValue()).allSatisfy(ticket -> {
			assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SPENT);
			assertThat(ticket.getVersion()).isEqualTo(2);
			assertThat(ticket.getUpdatedAt()).isEqualTo(NOW_MICROS);
		});
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<TicketHistory>> saved = ArgumentCaptor.forClass(List.class);
		verify(historyRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).hasSize(2).allSatisfy(history -> {
			assertThat(history.getOperationType()).isEqualTo(TicketOperationType.USE);
			assertThat(history.getEventEntryId()).isEqualTo(ENTRY_ID);
			assertThat(history.getCreatedAt()).isEqualTo(NOW_MICROS);
			assertThat(history.getReason()).isEqualTo("이벤트 응모");
		});
	}

	@Test
	@DisplayName("후보가 요청 수량보다 적으면 TICKET-006으로 실패하고 아무것도 저장하지 않는다")
	void insufficientTicketsFail() {
		// given
		when(ticketRepository.findUsableForUpdate(USER_ID, NOW_MICROS, 3)).thenReturn(List.of(ticket(TicketGrade.BRONZE)));
		// when
		// then
		assertThatThrownBy(() -> service.use(command(3)))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INSUFFICIENT));
		verify(ticketRepository, never()).updateAll(any());
		verify(historyRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("같은 응모의 사용 이력이 이미 있으면 추가로 차감하지 않고 기존 결과를 replayed=true로 돌려준다")
	void replaysExistingUse() {
		// given
		Ticket spent = ticket(TicketGrade.GOLD).use(NOW_MICROS);
		TicketHistory history = TicketHistory.use(spent, ENTRY_ID, "이벤트 응모");
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(history));
		when(ticketRepository.findAllByIds(anyCollection())).thenReturn(List.of(spent));
		// when
		UseResult result = service.use(command(1));
		// then
		assertThat(result.isReplayed()).isTrue();
		assertThat(result.getUsedAt()).isEqualTo(NOW_MICROS);
		assertThat(result.getTickets()).extracting(ticket -> ticket.getTicketId()).containsExactly(spent.getId());
		assertThat(result.getTickets().get(0).getGrade()).isEqualTo(TicketGrade.GOLD);
		verify(ticketRepository, never()).findUsableForUpdate(any(), any(), anyInt());
		verify(ticketRepository, never()).updateAll(any());
		verify(historyRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("이미 차감된 응모에 수량이나 사용자가 다른 요청이 오면 TICKET-008이다")
	void mismatchedReplayFails() {
		// given
		Ticket spent = ticket(TicketGrade.BRONZE).use(NOW_MICROS);
		TicketHistory history = TicketHistory.use(spent, ENTRY_ID, "이벤트 응모");
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(history));
		when(ticketRepository.findAllByIds(anyCollection())).thenReturn(List.of(spent));
		// when
		// then
		assertThatThrownBy(() -> service.use(command(2)))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_USE_MISMATCH));
		assertThatThrownBy(() -> service.use(new UseCommand(UUID.randomUUID(), ENTRY_ID, 1, "이벤트 응모")))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_USE_MISMATCH));
	}

	@Test
	@DisplayName("요청이 없거나 사용자·응모 ID가 없거나 수량이 1 미만이면 저장소를 건드리지 않고 TICKET-005다")
	void rejectsInvalidCommand() {
		// given
		// when
		// then
		for (UseCommand invalid : new UseCommand[] {
				null,
				new UseCommand(null, ENTRY_ID, 1, "사유"),
				new UseCommand(USER_ID, null, 1, "사유"),
				new UseCommand(USER_ID, ENTRY_ID, 0, "사유"),
				new UseCommand(USER_ID, ENTRY_ID, Integer.MAX_VALUE + 1L, "사유")}) {
			assertThatThrownBy(() -> service.use(invalid))
					.isInstanceOfSatisfying(TicketException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INVALID_USE));
		}
		verifyNoInteractions(ticketRepository, historyRepository);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {" ", "\t"})
	@DisplayName("사유가 비어 있으면 TICKET-005다")
	void rejectsBlankReason(String reason) {
		// given
		// when
		// then
		assertThatThrownBy(() -> service.use(new UseCommand(USER_ID, ENTRY_ID, 1, reason)))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INVALID_USE));
		verifyNoInteractions(ticketRepository, historyRepository);
	}
}
