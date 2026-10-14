package com.getddo.core.event.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.getddo.core.event.domain.EventStatus;
import com.getddo.core.event.exception.EventErrorCode;
import com.getddo.core.event.exception.EventException;
import com.getddo.core.event.repository.EventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventStatusChangeServiceTest {

	private static final UUID EVENT = UUID.fromString("00000000-0000-0000-0000-000000000196");
	@Mock private EventRepository events;
	@InjectMocks private EventStatusChangeService service;

	@Test
	@DisplayName("잠금 조회한 실제 상태가 예상과 일치하면 변경할 상태를 저장한다")
	void changesMatchingStatusAfterLock() {
		// given
		when(events.findStatusByIdForUpdate(EVENT)).thenReturn(Optional.of(EventStatus.CLOSED));
		// when
		service.changeStatus(EVENT, EventStatus.CLOSED, EventStatus.DRAW_CONFIRMED);
		// then
		InOrder order = inOrder(events);
		order.verify(events).findStatusByIdForUpdate(EVENT);
		order.verify(events).updateStatus(EVENT, EventStatus.DRAW_CONFIRMED);
	}

	@Test
	@DisplayName("실제 상태가 예상과 다르면 상태를 저장하지 않고 충돌을 전달한다")
	void rejectsUnexpectedStatusWithoutWrite() {
		// given
		when(events.findStatusByIdForUpdate(EVENT)).thenReturn(Optional.of(EventStatus.PUBLISHED));
		// when / then
		assertError(EventErrorCode.STATUS_CHANGE_CONFLICT, EVENT, EventStatus.CLOSED, EventStatus.DRAW_CONFIRMED);
		verify(events, never()).updateStatus(any(), any());
	}

	@Test
	@DisplayName("취소 상태가 예상과 일치해도 다른 상태로 되돌리지 않는다")
	void rejectsLeavingCanceledStatus() {
		// given
		when(events.findStatusByIdForUpdate(EVENT)).thenReturn(Optional.of(EventStatus.CANCELED));
		// when / then
		assertError(EventErrorCode.STATUS_CHANGE_CONFLICT, EVENT, EventStatus.CANCELED, EventStatus.OPEN);
		verify(events, never()).updateStatus(any(), any());
	}

	@ParameterizedTest
	@EnumSource(value = EventStatus.class, names = {"CLOSED", "CANCELED"})
	@DisplayName("현재·예상·변경 상태가 모두 같으면 취소 상태를 포함해 추가 저장을 하지 않는다")
	void sameStatusDoesNotWrite(EventStatus status) {
		// given
		when(events.findStatusByIdForUpdate(EVENT)).thenReturn(Optional.of(status));
		// when
		service.changeStatus(EVENT, status, status);
		// then
		verify(events, never()).updateStatus(any(), any());
	}

	@Test
	@DisplayName("미존재·삭제 이벤트의 잠금 조회 결과가 없으면 찾을 수 없음을 전달한다")
	void rejectsMissingStatus() {
		// given
		when(events.findStatusByIdForUpdate(EVENT)).thenReturn(Optional.empty());
		// when / then
		assertError(EventErrorCode.EVENT_NOT_FOUND, EVENT, EventStatus.CLOSED, EventStatus.DRAW_CONFIRMED);
		verify(events, never()).updateStatus(any(), any());
	}

	@Test
	@DisplayName("이벤트 식별자나 상태가 null이면 저장소 호출 전에 거절한다")
	void rejectsNullArgumentsBeforeRepositoryCalls() {
		// given / when / then
		assertError(EventErrorCode.EVENT_NOT_FOUND, null, EventStatus.CLOSED, EventStatus.DRAW_CONFIRMED);
		assertError(EventErrorCode.INVALID_STATUS_CHANGE, EVENT, null, EventStatus.DRAW_CONFIRMED);
		assertError(EventErrorCode.INVALID_STATUS_CHANGE, EVENT, EventStatus.CLOSED, null);
		verifyNoInteractions(events);
	}

	private void assertError(EventErrorCode code, UUID eventId, EventStatus expected, EventStatus next) {
		assertThatThrownBy(() -> service.changeStatus(eventId, expected, next))
				.isInstanceOfSatisfying(EventException.class,
						failure -> assertThat(failure.getErrorCode()).isEqualTo(code));
	}
}
