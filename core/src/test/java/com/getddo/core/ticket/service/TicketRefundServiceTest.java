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
import com.getddo.core.ticket.domain.RefundCommand;
import com.getddo.core.ticket.domain.RefundResult;
import com.getddo.core.ticket.domain.Ticket;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketHistory;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.TicketHistoryRepository;
import com.getddo.core.ticket.repository.TicketRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketRefundServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-15T03:00:00.123456789Z");
	private static final Instant NOW_MICROS = Instant.parse("2026-09-15T03:00:00.123456Z");
	private static final Instant USED_AT = Instant.parse("2026-09-10T03:00:00Z");
	private static final Instant SEPTEMBER_END = Instant.parse("2026-09-30T15:00:00Z");
	private static final Instant OCTOBER_END = Instant.parse("2026-10-31T15:00:00Z");
	private static final Instant CREATED_AT = Instant.parse("2026-09-01T00:00:00Z");
	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID ENTRY_ID = UUID.randomUUID();

	@Mock
	private TicketRepository ticketRepository;
	@Mock
	private TicketHistoryRepository historyRepository;

	private TicketRefundService service;

	@BeforeEach
	void setUp() {
		service = new TicketRefundService(ticketRepository, historyRepository,
				new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	}

	private static Ticket spentTicket(TicketGrade grade) {
		return new Ticket(UUID.randomUUID(), USER_ID, new GrantSource(GrantSourceType.MISSION, UUID.randomUUID()),
				grade, TicketStatus.SPENT, SEPTEMBER_END, 2, CREATED_AT, USED_AT);
	}

	private static TicketHistory useHistory(Ticket spent) {
		return withId(TicketHistory.use(spent, ENTRY_ID, "이벤트 응모"));
	}

	private static TicketHistory withId(TicketHistory history) {
		return new TicketHistory(UUID.randomUUID(), history.getTicketId(), history.getOperationType(),
				history.getTicketVersion(), history.getStatus(), history.getExpiresAt(), history.getEventEntryId(),
				history.getOriginalUseHistoryId(), history.getCorrectedHistoryId(), history.getReason(),
				history.getCreatedAt());
	}

	private static RefundCommand command() {
		return new RefundCommand(ENTRY_ID, "이벤트 취소");
	}

	@Test
	@DisplayName("사용된 응모권을 반환됨으로 바꾸고 반환 만료와 원본 사용 이력을 담은 반환 이력을 저장한다")
	void refundsSpentTickets() {
		// given
		Ticket spent = spentTicket(TicketGrade.GOLD);
		TicketHistory use = useHistory(spent);
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(use));
		when(historyRepository.findRefundsOf(anyCollection())).thenReturn(List.of());
		when(ticketRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(spent));
		// when
		RefundResult result = service.refund(command());
		// then
		assertThat(result.isReplayed()).isFalse();
		assertThat(result.getRefundedAt()).isEqualTo(NOW_MICROS);
		assertThat(result.getExpiresAt()).isEqualTo(OCTOBER_END);
		assertThat(result.getTickets()).extracting(ticket -> ticket.getTicketId()).containsExactly(spent.getId());
		assertThat(result.getTickets().get(0).getGrade()).isEqualTo(TicketGrade.GOLD);

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<Ticket>> updated = ArgumentCaptor.forClass(List.class);
		verify(ticketRepository).updateAll(updated.capture());
		assertThat(updated.getValue()).singleElement().satisfies(ticket -> {
			assertThat(ticket.getStatus()).isEqualTo(TicketStatus.RETURNED);
			assertThat(ticket.getVersion()).isEqualTo(3);
			assertThat(ticket.getExpiresAt()).isEqualTo(OCTOBER_END);
			assertThat(ticket.getGrade()).isEqualTo(TicketGrade.GOLD);
		});
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<TicketHistory>> saved = ArgumentCaptor.forClass(List.class);
		verify(historyRepository).saveAll(saved.capture());
		assertThat(saved.getValue()).singleElement().satisfies(history -> {
			assertThat(history.getOperationType()).isEqualTo(TicketOperationType.REFUND);
			assertThat(history.getOriginalUseHistoryId()).isEqualTo(use.getId());
			assertThat(history.getReason()).isEqualTo("이벤트 취소");
		});
	}

	@Test
	@DisplayName("사용 이력이 없는 응모는 TICKET-007이다")
	void refundWithoutUseFails() {
		// given
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of());
		// when
		// then
		assertThatThrownBy(() -> service.refund(command()))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_USE_NOT_FOUND));
		verify(ticketRepository, never()).updateAll(any());
	}

	@Test
	@DisplayName("모든 사용 이력에 반환 이력이 이미 있으면 추가로 반환하지 않고 기존 결과를 replayed=true로 돌려준다")
	void replaysExistingRefund() {
		// given
		Ticket spent = spentTicket(TicketGrade.SILVER);
		TicketHistory use = useHistory(spent);
		Ticket returned = spent.refund(NOW_MICROS, OCTOBER_END);
		TicketHistory refund = TicketHistory.refund(returned, use.getId(), "이벤트 취소");
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(use));
		when(historyRepository.findRefundsOf(anyCollection())).thenReturn(List.of(refund));
		when(ticketRepository.findAllByIds(anyCollection())).thenReturn(List.of(returned));
		// when
		RefundResult result = service.refund(command());
		// then
		assertThat(result.isReplayed()).isTrue();
		assertThat(result.getRefundedAt()).isEqualTo(NOW_MICROS);
		assertThat(result.getExpiresAt()).isEqualTo(OCTOBER_END);
		assertThat(result.getTickets().get(0).getGrade()).isEqualTo(TicketGrade.SILVER);
		verify(ticketRepository, never()).findAllForUpdate(anyCollection());
		verify(ticketRepository, never()).updateAll(any());
	}

	@Test
	@DisplayName("잠금을 기다리는 사이 같은 응모의 반환이 커밋됐으면 잠근 응모권의 최신 상태로 판정해 기존 결과를 돌려준다")
	void replaysRefundCommittedWhileWaitingForLock() {
		// given: 일반 조회 시점에는 반환 이력이 보이지 않지만, 잠가 읽은 응모권은 이미 반환된 상태다
		Ticket spent = spentTicket(TicketGrade.BRONZE);
		TicketHistory use = useHistory(spent);
		Ticket returned = spent.refund(NOW_MICROS, OCTOBER_END);
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(use));
		when(historyRepository.findRefundsOf(anyCollection())).thenReturn(List.of());
		when(ticketRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(returned));
		// when
		RefundResult result = service.refund(command());
		// then
		assertThat(result.isReplayed()).isTrue();
		assertThat(result.getRefundedAt()).isEqualTo(NOW_MICROS);
		assertThat(result.getExpiresAt()).isEqualTo(OCTOBER_END);
		assertThat(result.getTickets()).extracting(ticket -> ticket.getTicketId()).containsExactly(spent.getId());
		verify(ticketRepository, never()).updateAll(any());
		verify(historyRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("반환할 응모권이 사용 직후 상태가 아니면 TICKET-009로 실패하고 아무것도 저장하지 않는다")
	void changedTicketFails() {
		// given
		Ticket spent = spentTicket(TicketGrade.BRONZE);
		TicketHistory use = useHistory(spent);
		Ticket changed = new Ticket(spent.getId(), USER_ID, spent.getGrantSource(), spent.getGrade(),
				TicketStatus.EXPIRED, SEPTEMBER_END, 3, CREATED_AT, SEPTEMBER_END);
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(use));
		when(historyRepository.findRefundsOf(anyCollection())).thenReturn(List.of());
		when(ticketRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(changed));
		// when
		// then
		assertThatThrownBy(() -> service.refund(command()))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_REFUND_STATE_MISMATCH));
		verify(ticketRepository, never()).updateAll(any());
		verify(historyRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("요청이 없거나 응모 ID·사유가 비어 있으면 저장소를 건드리지 않고 TICKET-005다")
	void rejectsInvalidCommand() {
		// given
		// when
		// then
		for (RefundCommand invalid : new RefundCommand[] {
				null,
				new RefundCommand(null, "사유"),
				new RefundCommand(ENTRY_ID, null),
				new RefundCommand(ENTRY_ID, " ")}) {
			assertThatThrownBy(() -> service.refund(invalid))
					.isInstanceOfSatisfying(TicketException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_INVALID_USE));
		}
		verifyNoInteractions(ticketRepository, historyRepository);
	}

	@Test
	@DisplayName("잠근 응모권이 하나도 없으면 TICKET-009로 실패하고 아무것도 저장하지 않는다")
	void missingLockedTicketFails() {
		// given
		TicketHistory use = useHistory(spentTicket(TicketGrade.BRONZE));
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(use));
		when(historyRepository.findRefundsOf(anyCollection())).thenReturn(List.of());
		when(ticketRepository.findAllForUpdate(anyCollection())).thenReturn(List.of());
		// when
		// then
		assertThatThrownBy(() -> service.refund(command()))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_REFUND_STATE_MISMATCH));
		verify(ticketRepository, never()).updateAll(any());
		verify(historyRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("반환됨이어도 버전이 사용 버전보다 2 이상 크면 이미 반환된 것으로 보지 않고 TICKET-009로 실패한다")
	void returnedTicketTouchedAgainFails() {
		// given: 사용 버전 2, 현재 버전 4 (반환 뒤 다른 처리가 끼어들었다)
		Ticket spent = spentTicket(TicketGrade.BRONZE);
		TicketHistory use = useHistory(spent);
		Ticket touched = new Ticket(spent.getId(), USER_ID, spent.getGrantSource(), spent.getGrade(),
				TicketStatus.RETURNED, OCTOBER_END, use.getTicketVersion() + 2, CREATED_AT, NOW_MICROS);
		when(historyRepository.findUseHistories(ENTRY_ID)).thenReturn(List.of(use));
		when(historyRepository.findRefundsOf(anyCollection())).thenReturn(List.of());
		when(ticketRepository.findAllForUpdate(anyCollection())).thenReturn(List.of(touched));
		// when
		// then
		assertThatThrownBy(() -> service.refund(command()))
				.isInstanceOfSatisfying(TicketException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(TicketErrorCode.TICKET_REFUND_STATE_MISMATCH));
		verify(ticketRepository, never()).updateAll(any());
		verify(historyRepository, never()).saveAll(any());
	}
}
