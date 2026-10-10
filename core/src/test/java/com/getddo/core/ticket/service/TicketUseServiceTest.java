package com.getddo.core.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import org.mockito.InOrder;
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
import com.getddo.core.ticket.domain.UseSelection;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.TicketHistoryRepository;
import com.getddo.core.ticket.repository.TicketRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
		return ticketOf(USER_ID, grade);
	}

	private static Ticket ticketOf(UUID userId, TicketGrade grade) {
		return new Ticket(UUID.randomUUID(), userId, new GrantSource(GrantSourceType.MISSION, UUID.randomUUID()),
				grade, TicketStatus.AVAILABLE, EXPIRES_AT, 1, CREATED_AT, CREATED_AT);
	}

	private static UseSelection selection(TicketGrade grade, long count) {
		return new UseSelection(grade, count);
	}

	private static UseCommand command(UseSelection... selections) {
		return new UseCommand(USER_ID, ENTRY_ID, List.of(selections), "이벤트 응모");
	}

	@Test
	@DisplayName("고른 등급마다 정해진 순서로 잠가 차감하고 마이크로초로 자른 시각을 응모권과 이력에 쓴다")
	void locksSelectedGroupsInOrderAndUsesThem() {
		// given: 요청은 골드를 먼저 적었지만, 잠금은 등급 순서(브론즈부터)로 건다
		Ticket bronze = ticket(TicketGrade.BRONZE);
		Ticket anotherBronze = ticket(TicketGrade.BRONZE);
		Ticket gold = ticket(TicketGrade.GOLD);
		when(ticketRepository.findUsableForUpdate(USER_ID, TicketGrade.BRONZE, NOW_MICROS, 2))
				.thenReturn(List.of(bronze, anotherBronze));
		when(ticketRepository.findUsableForUpdate(USER_ID, TicketGrade.GOLD, NOW_MICROS, 1))
				.thenReturn(List.of(gold));
		UseCommand command = command(selection(TicketGrade.GOLD, 1), selection(TicketGrade.BRONZE, 2));
		// when
		UseResult result = service.use(command);
		// then
		assertThat(result.isReplayed()).isFalse();
		assertThat(result.getUsedAt()).isEqualTo(NOW_MICROS);
		assertThat(result.getTickets()).extracting(ticket -> ticket.getTicketId())
				.containsExactly(bronze.getId(), anotherBronze.getId(), gold.getId());
		assertThat(result.countByGrade()).containsEntry(TicketGrade.BRONZE, 2L)
				.containsEntry(TicketGrade.SILVER, 0L).containsEntry(TicketGrade.GOLD, 1L);
		InOrder order = inOrder(ticketRepository);
		order.verify(ticketRepository).findUsableForUpdate(USER_ID, TicketGrade.BRONZE, NOW_MICROS, 2);
		order.verify(ticketRepository).findUsableForUpdate(USER_ID, TicketGrade.GOLD, NOW_MICROS, 1);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Ticket>> updated = ArgumentCaptor.forClass(List.class);
		verify(ticketRepository).updateAll(updated.capture());
		assertThat(updated.getValue()).hasSize(3).allSatisfy(ticket -> {
			assertThat(ticket.getStatus()).isEqualTo(TicketStatus.SPENT);
			assertThat(ticket.getVersion()).isEqualTo(2);
			assertThat(ticket.getUpdatedAt()).isEqualTo(NOW_MICROS);
		});
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<TicketHistory>> saved = ArgumentCaptor.forClass(List.class);
		verify(historyRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).hasSize(3).allSatisfy(history -> {
			assertThat(history.getOperationType()).isEqualTo(TicketOperationType.USE);
			assertThat(history.getEventEntryId()).isEqualTo(ENTRY_ID);
			assertThat(history.getCreatedAt()).isEqualTo(NOW_MICROS);
			assertThat(history.getReason()).isEqualTo("이벤트 응모");
		});
	}

	@Test
	@DisplayName("고른 등급의 쓸 수 있는 응모권이 고른 장수보다 적으면 TICKET-006으로 실패하고 아무것도 저장하지 않는다")
	void insufficientGroupFails() {
		// given
		when(ticketRepository.findUsableForUpdate(USER_ID, TicketGrade.BRONZE, NOW_MICROS, 3))
				.thenReturn(List.of(ticket(TicketGrade.BRONZE)));
		// when
		// then
		assertThatThrownBy(() -> service.use(command(selection(TicketGrade.BRONZE, 3))))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INSUFFICIENT));
		verify(ticketRepository, never()).updateAll(any());
		verify(historyRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("같은 응모의 사용 이력이 이미 있고 요청이 같으면 추가로 차감하지 않고 기존 결과를 replayed=true로 돌려준다")
	void replaysExistingUse() {
		// given
		Ticket spent = ticket(TicketGrade.GOLD).use(NOW_MICROS);
		TicketHistory history = TicketHistory.use(spent, ENTRY_ID, "이벤트 응모");
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(history));
		when(ticketRepository.findAllByIds(anyCollection())).thenReturn(List.of(spent));
		// when
		UseResult result = service.use(command(selection(TicketGrade.GOLD, 1)));
		// then
		assertThat(result.isReplayed()).isTrue();
		assertThat(result.getUsedAt()).isEqualTo(NOW_MICROS);
		assertThat(result.getTickets()).extracting(ticket -> ticket.getTicketId()).containsExactly(spent.getId());
		assertThat(result.getTickets().get(0).getGrade()).isEqualTo(TicketGrade.GOLD);
		verify(ticketRepository, never()).findUsableForUpdate(any(), any(), any(), anyInt());
		verify(ticketRepository, never()).updateAll(any());
		verify(historyRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("이미 차감된 응모에 다른 장수·등급·사용자의 요청이 오면 TICKET-008이다")
	void mismatchedReplayFails() {
		// given
		Ticket spent = ticket(TicketGrade.BRONZE).use(NOW_MICROS);
		TicketHistory history = TicketHistory.use(spent, ENTRY_ID, "이벤트 응모");
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(history));
		when(ticketRepository.findAllByIds(anyCollection())).thenReturn(List.of(spent));
		// when
		// then
		assertTicket008(command(selection(TicketGrade.BRONZE, 2)));
		assertTicket008(command(selection(TicketGrade.SILVER, 1)));
		assertTicket008(command(selection(TicketGrade.BRONZE, 1), selection(TicketGrade.SILVER, 1)));
		assertTicket008(new UseCommand(UUID.randomUUID(), ENTRY_ID, List.of(selection(TicketGrade.BRONZE, 1)),
				"이벤트 응모"));
	}

	@Test
	@DisplayName("여러 장 중 일부만 다른 사용자의 응모권이면 TICKET-008로 실패하고 아무것도 저장하지 않는다")
	void mismatchedUserInSecondHistoryFails() {
		// given
		Ticket mine = ticket(TicketGrade.BRONZE).use(NOW_MICROS);
		Ticket others = ticketOf(UUID.randomUUID(), TicketGrade.SILVER).use(NOW_MICROS);
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(
				TicketHistory.use(mine, ENTRY_ID, "이벤트 응모"), TicketHistory.use(others, ENTRY_ID, "이벤트 응모")));
		when(ticketRepository.findAllByIds(anyCollection())).thenReturn(List.of(mine, others));
		// when
		// then
		assertTicket008(command(selection(TicketGrade.BRONZE, 1), selection(TicketGrade.SILVER, 1)));
		verify(ticketRepository, never()).updateAll(any());
		verify(historyRepository, never()).saveAll(any());
	}

	private void assertTicket008(UseCommand command) {
		assertThatThrownBy(() -> service.use(command))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_USE_MISMATCH));
	}

	@Test
	@DisplayName("요청·사용자·응모 ID·선택이 비었거나 장수가 1 미만이거나 같은 등급이 겹치면 저장소를 건드리지 않고 TICKET-005다")
	void rejectsInvalidCommand() {
		// given
		List<UseSelection> withNull = new ArrayList<>();
		withNull.add(null);
		// when
		// then
		for (UseCommand invalid : new UseCommand[] {
				null,
				new UseCommand(null, ENTRY_ID, List.of(selection(TicketGrade.BRONZE, 1)), "사유"),
				new UseCommand(USER_ID, null, List.of(selection(TicketGrade.BRONZE, 1)), "사유"),
				new UseCommand(USER_ID, ENTRY_ID, null, "사유"),
				new UseCommand(USER_ID, ENTRY_ID, List.of(), "사유"),
				new UseCommand(USER_ID, ENTRY_ID, withNull, "사유"),
				command(selection(TicketGrade.BRONZE, 0)),
				command(selection(TicketGrade.BRONZE, -1)),
				command(selection(TicketGrade.BRONZE, Integer.MAX_VALUE + 1L)),
				command(selection(TicketGrade.BRONZE, 1), selection(TicketGrade.BRONZE, 2)),
				command(selection(TicketGrade.BRONZE, Integer.MAX_VALUE), selection(TicketGrade.SILVER, 1))}) {
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
		assertThatThrownBy(() -> service.use(
				new UseCommand(USER_ID, ENTRY_ID, List.of(selection(TicketGrade.BRONZE, 1)), reason)))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INVALID_USE));
		verifyNoInteractions(ticketRepository, historyRepository);
	}

	private static Clock sequenceOf(Instant... instants) {
		java.util.Iterator<Instant> times = List.of(instants).iterator();
		return new Clock() {
			@Override
			public ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(ZoneId zone) {
				return this;
			}

			@Override
			public Instant instant() {
				return times.next();
			}
		};
	}

	@Test
	@DisplayName("잠금을 기다리는 사이 고른 응모권이 만료되면 처리 시각으로 다시 골라 뒤에 만료되는 응모권으로 대체한다")
	void replacesTicketThatExpiredWhileWaitingForLocks() {
		// given: 첫 시도의 후보는 만료 직전, 잠금을 모두 쥔 뒤에는 만료 시각이 지났다
		Instant beforeExpiry = Instant.parse("2026-09-30T14:59:59.999999Z");
		Instant afterExpiry = Instant.parse("2026-09-30T15:00:01Z");
		TicketUseService advancingService = new TicketUseService(ticketRepository, historyRepository,
				new TimeProvider(sequenceOf(beforeExpiry, afterExpiry, afterExpiry, afterExpiry)));
		Ticket expiring = ticket(TicketGrade.BRONZE);
		Ticket later = new Ticket(UUID.randomUUID(), USER_ID, new GrantSource(GrantSourceType.MISSION, UUID.randomUUID()),
				TicketGrade.BRONZE, TicketStatus.AVAILABLE, Instant.parse("2026-10-31T15:00:00Z"), 1, CREATED_AT,
				CREATED_AT);
		when(ticketRepository.findUsableForUpdate(USER_ID, TicketGrade.BRONZE, beforeExpiry, 1))
				.thenReturn(List.of(expiring));
		when(ticketRepository.findUsableForUpdate(USER_ID, TicketGrade.BRONZE, afterExpiry, 1))
				.thenReturn(List.of(later));
		// when
		UseResult result = advancingService.use(command(selection(TicketGrade.BRONZE, 1)));
		// then
		assertThat(result.getTickets()).extracting(used -> used.getTicketId()).containsExactly(later.getId());
		assertThat(result.getUsedAt()).isEqualTo(afterExpiry);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Ticket>> updated = ArgumentCaptor.forClass(List.class);
		verify(ticketRepository).updateAll(updated.capture());
		assertThat(updated.getValue()).extracting(Ticket::getId).containsExactly(later.getId());
	}

	@Test
	@DisplayName("다시 골라도 매번 만료되면 정해진 횟수 뒤 TICKET-006으로 실패하고 아무것도 저장하지 않는다")
	void givesUpWhenSelectionKeepsExpiring() {
		// given
		Instant beforeExpiry = Instant.parse("2026-09-30T14:59:59.999999Z");
		Instant afterExpiry = Instant.parse("2026-09-30T15:00:01Z");
		TicketUseService advancingService = new TicketUseService(ticketRepository, historyRepository,
				new TimeProvider(sequenceOf(beforeExpiry, afterExpiry, beforeExpiry, afterExpiry, beforeExpiry,
						afterExpiry)));
		when(ticketRepository.findUsableForUpdate(USER_ID, TicketGrade.BRONZE, beforeExpiry, 1))
				.thenReturn(List.of(ticket(TicketGrade.BRONZE)));
		// when
		// then
		assertThatThrownBy(() -> advancingService.use(command(selection(TicketGrade.BRONZE, 1))))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INSUFFICIENT));
		verify(ticketRepository, times(3)).findUsableForUpdate(USER_ID, TicketGrade.BRONZE, beforeExpiry, 1);
		verify(ticketRepository, never()).updateAll(any());
		verify(historyRepository, never()).saveAll(any());
	}
}
