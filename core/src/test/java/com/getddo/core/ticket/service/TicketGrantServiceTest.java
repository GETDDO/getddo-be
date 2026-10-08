package com.getddo.core.ticket.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.ticket.domain.GrantCommand;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceClaim;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.GrantedTicket;
import com.getddo.core.ticket.domain.Ticket;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketGradeDrawer;
import com.getddo.core.ticket.domain.TicketHistory;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.domain.TicketStatus;
import com.getddo.core.ticket.exception.TicketErrorCode;
import com.getddo.core.ticket.exception.TicketException;
import com.getddo.core.ticket.repository.GrantSourceRepository;
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
class TicketGrantServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-15T03:00:00.123456789Z");
	private static final Instant NOW_MICROS = Instant.parse("2026-09-15T03:00:00.123456Z");
	private static final Instant SEPTEMBER_END = Instant.parse("2026-09-30T15:00:00Z");
	private static final UUID USER_ID = UUID.randomUUID();
	private static final GrantSource MISSION = new GrantSource(GrantSourceType.MISSION, UUID.randomUUID());
	private static final GrantSource ATTENDANCE = new GrantSource(GrantSourceType.ATTENDANCE, UUID.randomUUID());

	@Mock
	private GrantSourceRepository grantSourceRepository;
	@Mock
	private TicketRepository ticketRepository;
	@Mock
	private TicketHistoryRepository historyRepository;
	@Mock
	private TicketGradeDrawer gradeDrawer;

	private CountingClock clock;
	private TicketGrantService service;

	@BeforeEach
	void setUp() {
		clock = new CountingClock(NOW);
		service = new TicketGrantService(grantSourceRepository, ticketRepository, historyRepository, gradeDrawer,
				new TimeProvider(clock));
	}

	private static GrantCommand command(GrantSource source, long quantity) {
		return new GrantCommand(USER_ID, source, quantity, "테스트 보상");
	}

	private static void assertErrorCode(Runnable call, TicketErrorCode expected) {
		assertThatThrownBy(call::run)
				.isInstanceOf(TicketException.class)
				.extracting(error -> ((TicketException) error).getErrorCode())
				.isEqualTo(expected);
	}

	@Nested
	@DisplayName("grant 입력 검증")
	class InputValidation {

		@ParameterizedTest
		@ValueSource(longs = {0, -1})
		@DisplayName("수량이 1 미만이면 저장소를 조회하지 않고 거절한다")
		void rejectsNonPositiveQuantity(long quantity) {
			// given
			GrantCommand command = command(ATTENDANCE, quantity);
			// when
			// then
			assertErrorCode(() -> service.grant(command), TicketErrorCode.TICKET_INVALID_GRANT);
			verifyNoInteractions(grantSourceRepository, ticketRepository, historyRepository, gradeDrawer);
		}

		@Test
		@DisplayName("미션·게임 보상은 수량이 1이 아니면 거절한다")
		void rejectsMultipleTicketsForMissionAndGame() {
			// given
			GrantCommand mission = command(MISSION, 2);
			GrantCommand game = command(new GrantSource(GrantSourceType.GAME, UUID.randomUUID()), 2);
			// when
			// then
			assertErrorCode(() -> service.grant(mission), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.grant(game), TicketErrorCode.TICKET_INVALID_GRANT);
			verifyNoInteractions(grantSourceRepository);
		}

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = {" ", "\t\n"})
		@DisplayName("사유가 비어 있으면 거절한다")
		void rejectsBlankReason(String reason) {
			// given
			GrantCommand command = new GrantCommand(USER_ID, MISSION, 1, reason);
			// when
			// then
			assertErrorCode(() -> service.grant(command), TicketErrorCode.TICKET_INVALID_GRANT);
			verifyNoInteractions(grantSourceRepository);
		}

		@Test
		@DisplayName("요청·사용자·청구 종류·청구 ID가 없으면 거절한다")
		void rejectsMissingIdentifiers() {
			// given
			GrantCommand noUser = new GrantCommand(null, MISSION, 1, "사유");
			GrantCommand noSource = new GrantCommand(USER_ID, null, 1, "사유");
			GrantCommand noType = new GrantCommand(USER_ID, new GrantSource(null, UUID.randomUUID()), 1, "사유");
			GrantCommand noClaim = new GrantCommand(USER_ID, new GrantSource(GrantSourceType.GAME, null), 1, "사유");
			// when
			// then
			assertErrorCode(() -> service.grant(null), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.grant(noUser), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.grant(noSource), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.grant(noType), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.grant(noClaim), TicketErrorCode.TICKET_INVALID_GRANT);
			verifyNoInteractions(grantSourceRepository);
		}
	}

	@Nested
	@DisplayName("grant 청구 검증")
	class ClaimValidation {

		@Test
		@DisplayName("청구 행이 없으면 거절한다")
		void rejectsMissingClaim() {
			// given
			when(grantSourceRepository.find(MISSION)).thenReturn(Optional.empty());
			// when
			// then
			assertErrorCode(() -> service.grant(command(MISSION, 1)), TicketErrorCode.TICKET_GRANT_SOURCE_NOT_FOUND);
			verifyNoInteractions(ticketRepository, historyRepository);
		}

		@Test
		@DisplayName("청구의 사용자가 요청과 다르면 기존 지급 조회 전에 거절한다")
		void rejectsOtherUsersClaim() {
			// given
			when(grantSourceRepository.find(MISSION))
					.thenReturn(Optional.of(new GrantSourceClaim(UUID.randomUUID(), 1)));
			// when
			// then
			assertErrorCode(() -> service.grant(command(MISSION, 1)), TicketErrorCode.TICKET_GRANT_SOURCE_MISMATCH);
			verifyNoInteractions(ticketRepository, historyRepository);
		}

		@Test
		@DisplayName("청구의 수량이 요청과 다르면 거절한다")
		void rejectsQuantityMismatch() {
			// given
			when(grantSourceRepository.find(ATTENDANCE)).thenReturn(Optional.of(new GrantSourceClaim(USER_ID, 2)));
			// when
			// then
			assertErrorCode(() -> service.grant(command(ATTENDANCE, 1)), TicketErrorCode.TICKET_GRANT_SOURCE_MISMATCH);
			verifyNoInteractions(ticketRepository, historyRepository);
		}
	}

	@Nested
	@DisplayName("grant 지급")
	class Grant {

		@Test
		@DisplayName("이미 지급된 청구면 추가로 만들지 않고 기존 결과를 replayed=true로 반환한다")
		void replaysExistingGrant() {
			// given
			when(grantSourceRepository.find(MISSION)).thenReturn(Optional.of(new GrantSourceClaim(USER_ID, 1)));
			Instant grantedAt = Instant.parse("2026-09-10T01:00:00Z");
			when(ticketRepository.findGranted(MISSION)).thenReturn(
					List.of(new GrantedTicket(UUID.randomUUID(), TicketGrade.SILVER, grantedAt, SEPTEMBER_END)));
			// when
			GrantResult result = service.grant(command(MISSION, 1));
			// then
			assertThat(result.isReplayed()).isTrue();
			assertThat(result.getGrade()).isEqualTo(TicketGrade.SILVER);
			assertThat(result.getQuantity()).isEqualTo(1);
			assertThat(result.getGrantedAt()).isEqualTo(grantedAt);
			assertThat(result.getExpiresAt()).isEqualTo(SEPTEMBER_END);
			verify(ticketRepository, never()).saveAll(any());
			verifyNoInteractions(historyRepository, gradeDrawer);
			assertThat(clock.reads()).isZero();
		}

		@Test
		@DisplayName("신규 지급은 시각을 한 번만 구하고 마이크로초로 잘라 응모권·이력·결과에 같은 값을 쓴다")
		void grantsWithSingleGrantedAt() {
			// given
			when(grantSourceRepository.find(ATTENDANCE)).thenReturn(Optional.of(new GrantSourceClaim(USER_ID, 2)));
			when(ticketRepository.findGranted(ATTENDANCE)).thenReturn(List.of());
			when(gradeDrawer.draw(GrantSourceType.ATTENDANCE)).thenReturn(TicketGrade.BRONZE);
			when(ticketRepository.saveAll(any())).thenAnswer(invocation -> withIds(invocation.getArgument(0)));
			// when
			GrantResult result = service.grant(command(ATTENDANCE, 2));
			// then
			assertThat(clock.reads()).isEqualTo(1);
			assertThat(result.isReplayed()).isFalse();
			assertThat(result.getQuantity()).isEqualTo(2);
			assertThat(result.getGrade()).isEqualTo(TicketGrade.BRONZE);
			assertThat(result.getGrantedAt()).isEqualTo(NOW_MICROS);
			assertThat(result.getExpiresAt()).isEqualTo(SEPTEMBER_END);

			ArgumentCaptor<List<Ticket>> tickets = ArgumentCaptor.captor();
			verify(ticketRepository).saveAll(tickets.capture());
			assertThat(tickets.getValue()).hasSize(2).allSatisfy(ticket -> {
				assertThat(ticket.getUserId()).isEqualTo(USER_ID);
				assertThat(ticket.getGrantSource()).isEqualTo(ATTENDANCE);
				assertThat(ticket.getGrade()).isEqualTo(TicketGrade.BRONZE);
				assertThat(ticket.getStatus()).isEqualTo(TicketStatus.AVAILABLE);
				assertThat(ticket.getVersion()).isEqualTo(1);
				assertThat(ticket.getExpiresAt()).isEqualTo(SEPTEMBER_END);
				assertThat(ticket.getCreatedAt()).isEqualTo(NOW_MICROS);
			});

			ArgumentCaptor<List<TicketHistory>> histories = ArgumentCaptor.captor();
			verify(historyRepository).saveAll(histories.capture());
			assertThat(histories.getValue()).hasSize(2).allSatisfy(history -> {
				assertThat(history.getOperationType()).isEqualTo(TicketOperationType.GRANT);
				assertThat(history.getReason()).isEqualTo("테스트 보상");
				assertThat(history.getCreatedAt()).isEqualTo(NOW_MICROS);
				assertThat(history.getExpiresAt()).isEqualTo(SEPTEMBER_END);
			});
		}

		@Test
		@DisplayName("등급은 한 번만 뽑아 한 지급 건의 응모권에 부여한다")
		void drawsGradeOncePerGrant() {
			// given
			GrantSource game = new GrantSource(GrantSourceType.GAME, UUID.randomUUID());
			when(grantSourceRepository.find(game)).thenReturn(Optional.of(new GrantSourceClaim(USER_ID, 1)));
			when(ticketRepository.findGranted(game)).thenReturn(List.of());
			when(gradeDrawer.draw(GrantSourceType.GAME)).thenReturn(TicketGrade.GOLD);
			when(ticketRepository.saveAll(any())).thenAnswer(invocation -> withIds(invocation.getArgument(0)));
			// when
			GrantResult result = service.grant(command(game, 1));
			// then
			assertThat(result.getGrade()).isEqualTo(TicketGrade.GOLD);
			ArgumentCaptor<List<Ticket>> tickets = ArgumentCaptor.captor();
			verify(ticketRepository).saveAll(tickets.capture());
			assertThat(tickets.getValue()).extracting(Ticket::getGrade).containsOnly(TicketGrade.GOLD);
		}

		private List<Ticket> withIds(List<Ticket> tickets) {
			return tickets.stream().map(ticket -> new Ticket(UUID.randomUUID(), ticket.getUserId(),
					ticket.getGrantSource(), ticket.getGrade(), ticket.getStatus(), ticket.getExpiresAt(),
					ticket.getVersion(), ticket.getCreatedAt(), ticket.getUpdatedAt())).toList();
		}
	}

	@Nested
	@DisplayName("findGrant")
	class FindGrant {

		@Test
		@DisplayName("지급된 응모권이 없으면 빈 값을 반환한다")
		void returnsEmptyWhenNotGranted() {
			// given
			when(ticketRepository.findGranted(MISSION)).thenReturn(List.of());
			// when
			Optional<GrantResult> result = service.findGrant(MISSION);
			// then
			assertThat(result).isEmpty();
		}

		@Test
		@DisplayName("지급된 응모권이 있으면 지급 당시 값으로 replayed=true 결과를 반환하고 청구는 조회하지 않는다")
		void returnsReplayedResult() {
			// given
			Instant grantedAt = Instant.parse("2026-09-10T01:00:00Z");
			when(ticketRepository.findGranted(ATTENDANCE)).thenReturn(List.of(
					new GrantedTicket(UUID.randomUUID(), TicketGrade.BRONZE, grantedAt, SEPTEMBER_END),
					new GrantedTicket(UUID.randomUUID(), TicketGrade.BRONZE, grantedAt, SEPTEMBER_END)));
			// when
			Optional<GrantResult> result = service.findGrant(ATTENDANCE);
			// then
			assertThat(result).get().satisfies(granted -> {
				assertThat(granted.isReplayed()).isTrue();
				assertThat(granted.getQuantity()).isEqualTo(2);
				assertThat(granted.getGrade()).isEqualTo(TicketGrade.BRONZE);
				assertThat(granted.getGrantedAt()).isEqualTo(grantedAt);
				assertThat(granted.getExpiresAt()).isEqualTo(SEPTEMBER_END);
			});
			verifyNoInteractions(grantSourceRepository);
		}

		@Test
		@DisplayName("청구 종류나 ID가 없으면 거절한다")
		void rejectsIncompleteSource() {
			// given
			// when
			// then
			assertErrorCode(() -> service.findGrant(null), TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.findGrant(new GrantSource(null, UUID.randomUUID())),
					TicketErrorCode.TICKET_INVALID_GRANT);
			assertErrorCode(() -> service.findGrant(new GrantSource(GrantSourceType.MISSION, null)),
					TicketErrorCode.TICKET_INVALID_GRANT);
			verifyNoInteractions(ticketRepository);
		}
	}

	/** 읽은 횟수를 세는 고정 시계. */
	private static final class CountingClock extends Clock {

		private final Instant instant;
		private final AtomicInteger reads = new AtomicInteger();

		CountingClock(Instant instant) {
			this.instant = instant;
		}

		int reads() {
			return reads.get();
		}

		@Override
		public Instant instant() {
			reads.incrementAndGet();
			return instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			throw new UnsupportedOperationException();
		}
	}
}
