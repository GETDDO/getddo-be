package com.getddo.core.event.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.event.domain.EventRegistration;
import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.domain.EventType;
import com.getddo.core.event.domain.MembershipRule;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.repository.EventActorRepository;
import com.getddo.core.event.repository.EventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventRegistrationServiceTest {
	private static final UUID ADMIN_ID = UUID.fromString("0199abcd-1234-7000-8000-000000000001");
	private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");
	private EventRepository events;
	private EventActorRepository actors;
	private EventRegistrationService service;

	@BeforeEach
	void setUp() {
		events = mock(EventRepository.class);
		actors = mock(EventActorRepository.class);
		when(actors.findById(ADMIN_ID)).thenReturn(Optional.of(new EventActorRepository.Actor(true, true)));
		service = new EventRegistrationService(events, actors,
				new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	}

	@Test
	void validRegistrationPassesScheduledEventAndAllPrizesToRepository() {
		EventRegistration registration = validRegistration();

		service.register(registration);

		verify(events).create(registration, EventStatus.SCHEDULED);
	}

	@Test
	void pastStartUsesOpenOrClosedAccordingToCurrentTime() {
		EventRegistration source = validRegistration();
		EventRegistration open = withPeriod(source, NOW.minusSeconds(1), NOW.plusSeconds(1));
		EventRegistration closed = withPeriod(source, NOW.minusSeconds(2), NOW);

		service.register(open);
		service.register(closed);

		verify(events).create(open, EventStatus.OPEN);
		verify(events).create(closed, EventStatus.CLOSED);
	}

	@Test
	void noTicketEventUsesNoWeightingAndNoTicketLimit() {
		EventRegistration source = validRegistration();
		EventRegistration noTicket = new EventRegistration(source.actorId(), source.title(),
				source.description(), source.imageKey(), EventType.NO_TICKET, false,
				null, source.membershipRule(), source.startsAt(), source.endsAt(), source.prizes());

		service.register(noTicket);

		verify(events).create(noTicket, EventStatus.SCHEDULED);
	}

	@Test
	void adminCanRegisterUnlimitedWeightedEventRegardlessOfPeriod() {
		EventRegistration source = validRegistration();
		EventRegistration unlimited = new EventRegistration(source.actorId(), source.title(),
				source.description(), source.imageKey(), EventType.TICKET, true,
				null, source.membershipRule(), source.startsAt(), source.endsAt(), source.prizes());

		service.register(unlimited);

		verify(events).create(unlimited, EventStatus.SCHEDULED);
	}

	@Test
	void duplicatePrizeRankFailsBeforeAnyWrite() {
		EventRegistration source = validRegistration();
		EventRegistration duplicate = new EventRegistration(source.actorId(), source.title(),
				source.description(), source.imageKey(), source.eventType(), source.weightingEnabled(),
				source.maxTicketsPerUser(), source.membershipRule(), source.startsAt(), source.endsAt(),
				List.of(source.prizes().getFirst(),
						new EventRegistration.Prize(1, "다른 경품", null, null, 2)));

		assertThatThrownBy(() -> service.register(duplicate))
				.isInstanceOf(BusinessException.class)
				.satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
						.isEqualTo(EventErrorCode.INVALID_PRIZES));
		verifyNoInteractions(events);
	}

	@Test
	void incompatibleTicketRuleFailsBeforeAnyWrite() {
		EventRegistration source = validRegistration();
		EventRegistration invalid = new EventRegistration(source.actorId(), source.title(),
				source.description(), source.imageKey(), EventType.NO_TICKET, true,
				5, source.membershipRule(), source.startsAt(), source.endsAt(), source.prizes());

		assertThatThrownBy(() -> service.register(invalid))
				.isInstanceOf(BusinessException.class)
				.satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
						.isEqualTo(EventErrorCode.INVALID_CONFIGURATION));
		verifyNoInteractions(events);
	}

	@Test
	void nonAdminCannotRegister() {
		when(actors.findById(ADMIN_ID)).thenReturn(Optional.of(new EventActorRepository.Actor(true, false)));

		assertThatThrownBy(() -> service.register(validRegistration()))
				.isInstanceOf(BusinessException.class)
				.satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
						.isEqualTo(EventErrorCode.ADMIN_REQUIRED));
		verifyNoInteractions(events);
	}

	@ParameterizedTest
	@ValueSource(strings = {"x", "가", "😀", "A가😀"})
	@DisplayName("영문·한글·이모지 설명은 UTF-8 65,535바이트까지 등록할 수 있다")
	void acceptsDescriptionsAtUtf8ByteLimit(String unit) {
		// given
		String description = descriptionAtTextLimit(unit);
		EventRegistration registration = withDescriptions(description, description);

		// when
		service.register(registration);

		// then
		verify(events).create(registration, EventStatus.SCHEDULED);
	}

	@ParameterizedTest
	@ValueSource(strings = {"x", "가", "😀", "A가😀"})
	@DisplayName("이벤트 설명이 UTF-8 상한을 1바이트 넘으면 저장 전에 거절한다")
	void rejectsOversizedEventDescriptionBeforeAnyWrite(String unit) {
		// given
		EventRegistration registration = withDescriptions(descriptionAtTextLimit(unit) + "x", null);

		// when / then
		assertThatThrownBy(() -> service.register(registration))
				.isInstanceOf(BusinessException.class)
				.satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
						.isEqualTo(EventErrorCode.INVALID_DETAILS));
		verifyNoInteractions(events);
	}

	@ParameterizedTest
	@ValueSource(strings = {"x", "가", "😀", "A가😀"})
	@DisplayName("경품 설명이 UTF-8 상한을 1바이트 넘으면 저장 전에 거절한다")
	void rejectsOversizedPrizeDescriptionBeforeAnyWrite(String unit) {
		// given
		EventRegistration registration = withDescriptions("이벤트 설명", descriptionAtTextLimit(unit) + "x");

		// when / then
		assertThatThrownBy(() -> service.register(registration))
				.isInstanceOf(BusinessException.class)
				.satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
						.isEqualTo(EventErrorCode.INVALID_PRIZES));
		verifyNoInteractions(events);
	}

	private String descriptionAtTextLimit(String unit) {
		int unitBytes = unit.getBytes(StandardCharsets.UTF_8).length;
		return unit.repeat(65_535 / unitBytes) + "x".repeat(65_535 % unitBytes);
	}

	private EventRegistration withDescriptions(String description, String prizeDescription) {
		EventRegistration source = validRegistration();
		EventRegistration.Prize prize = source.prizes().getFirst();
		return new EventRegistration(source.actorId(), source.title(), description, source.imageKey(),
				source.eventType(), source.weightingEnabled(), source.maxTicketsPerUser(), source.membershipRule(),
				source.startsAt(), source.endsAt(),
				List.of(new EventRegistration.Prize(prize.rank(), prize.name(), prizeDescription,
						prize.imageKey(), prize.winnerCount()), source.prizes().get(1)));
	}

	private EventRegistration validRegistration() {
		return new EventRegistration(ADMIN_ID, "가을 이벤트", "경품 응모", null,
				EventType.TICKET, true, 5, MembershipRule.vip,
				NOW.plusSeconds(3600), NOW.plusSeconds(7200),
				List.of(new EventRegistration.Prize(1, "경품 A", null, null, 1),
						new EventRegistration.Prize(2, "경품 B", null, null, 3)));
	}

	private EventRegistration withPeriod(EventRegistration source, Instant startsAt, Instant endsAt) {
		return new EventRegistration(source.actorId(), source.title(), source.description(), source.imageKey(),
				source.eventType(), source.weightingEnabled(), source.maxTicketsPerUser(), source.membershipRule(),
				startsAt, endsAt, source.prizes());
	}
}
