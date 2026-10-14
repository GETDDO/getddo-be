package com.getddo.core.entry.service;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import com.getddo.core.entry.domain.EntryCommand;
import com.getddo.core.entry.domain.EntryReceipt;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.user.domain.Membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntryServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID EVENT_ID = UUID.randomUUID();
	private static final UUID ENTRY_ID = UUID.randomUUID();

	@Mock
	private EntryRecorder recorder;
	@Mock
	private EntryRejectionRecorder rejectionRecorder;

	private EntryService service;

	@BeforeEach
	void setUp() {
		service = new EntryService(recorder, rejectionRecorder);
	}

	private static EntryCommand command() {
		return new EntryCommand(USER_ID, false, Membership.VIP, EVENT_ID, ENTRY_ID, Map.of(TicketGrade.GOLD, 1L));
	}

	private static EntryReceipt receipt() {
		return new EntryReceipt(ENTRY_ID, EVENT_ID, "이벤트", Map.of(TicketGrade.GOLD, 1L),
				Instant.parse("2026-09-15T03:00:00Z"), true);
	}

	@Test
	@DisplayName("정상 처리는 한 번만 처리하고 결과를 그대로 돌려준다")
	void processesOnce() {
		// given
		EntryCommand command = command();
		EntryReceipt receipt = receipt();
		when(recorder.record(command)).thenReturn(receipt);
		// when
		EntryReceipt result = service.enter(command);
		// then
		assertThat(result).isSameAs(receipt);
		verify(recorder, times(1)).record(command);
	}

	@Test
	@DisplayName("UNIQUE 위반이나 잠금 실패로 롤백되면 새 트랜잭션에서 한 번 다시 처리한다")
	void retriesOnceOnConcurrentConflict() {
		// given
		EntryCommand command = command();
		EntryReceipt receipt = receipt();
		when(recorder.record(command))
				.thenThrow(new DataIntegrityViolationException("duplicate", duplicateKey()))
				.thenReturn(receipt);
		// when
		EntryReceipt result = service.enter(command);
		// then
		assertThat(result).isSameAs(receipt);
		verify(recorder, times(2)).record(command);
	}

	@Test
	@DisplayName("UNIQUE 위반이 아닌 무결성 오류는 다시 처리하지 않고 그대로 던진다")
	void doesNotRetryNonUniqueIntegrityViolation() {
		// given
		EntryCommand command = command();
		when(recorder.record(command)).thenThrow(new DataIntegrityViolationException("foreign key",
				new SQLIntegrityConstraintViolationException("Cannot add or update a child row", "23000", 1452)));
		// when
		// then
		assertThatThrownBy(() -> service.enter(command)).isInstanceOf(DataIntegrityViolationException.class);
		verify(recorder, times(1)).record(command);
	}

	private static SQLException duplicateKey() {
		return new SQLIntegrityConstraintViolationException("Duplicate entry", "23000", 1062);
	}

	@Test
	@DisplayName("잠금 교착도 한 번 다시 처리하고 두 번째도 실패하면 그 예외를 그대로 던진다")
	void propagatesSecondFailure() {
		// given
		EntryCommand command = command();
		when(recorder.record(command))
				.thenThrow(new CannotAcquireLockException("deadlock"))
				.thenThrow(new CannotAcquireLockException("deadlock again"));
		// when
		// then
		assertThatThrownBy(() -> service.enter(command))
				.isInstanceOf(CannotAcquireLockException.class)
				.hasMessageContaining("again");
		verify(recorder, times(2)).record(command);
	}

	@Test
	@DisplayName("업무 규칙 위반은 재처리하지 않고 그대로 던진다")
	void doesNotRetryBusinessErrors() {
		// given
		EntryCommand command = command();
		when(recorder.record(command)).thenThrow(new EntryException(EntryErrorCode.EVENT_NOT_OPEN));
		// when
		// then
		assertThatThrownBy(() -> service.enter(command)).isInstanceOf(EntryException.class);
		verify(recorder, times(1)).record(command);
	}

	@Test
	@DisplayName("업무 거절은 이력 기록기에 넘기고 원래 거절을 그대로 던진다")
	void recordsBusinessRejectionAndRethrowsIt() {
		// given
		EntryCommand command = command();
		EntryException rejection = new EntryException(EntryErrorCode.ALREADY_ENTERED);
		when(recorder.record(command)).thenThrow(rejection);
		// when
		// then
		assertThatThrownBy(() -> service.enter(command)).isSameAs(rejection);
		verify(rejectionRecorder).record(command, rejection);
		verify(recorder, times(1)).record(command);
	}

	@Test
	@DisplayName("거절 이력이 같은 응모 ID로 동시에 먼저 저장되면 그 결과를 다시 처리해 돌려준다")
	void reprocessesWhenRejectionRaceLost() {
		// given
		EntryCommand command = command();
		EntryReceipt receipt = receipt();
		EntryException rejection = new EntryException(EntryErrorCode.TICKET_LIMIT_EXCEEDED);
		when(recorder.record(command)).thenThrow(rejection).thenReturn(receipt);
		org.mockito.Mockito.doThrow(new DataIntegrityViolationException("duplicate", duplicateKey()))
				.when(rejectionRecorder).record(command, rejection);
		// when
		EntryReceipt result = service.enter(command);
		// then
		assertThat(result).isSameAs(receipt);
		verify(recorder, times(2)).record(command);
	}

	@Test
	@DisplayName("거절 이력 저장이 실패해도 사용자에게는 원래 거절을 던지고 저장 실패는 덧붙여 남긴다")
	void keepsOriginalRejectionWhenRecordingFails() {
		// given
		EntryCommand command = command();
		EntryException rejection = new EntryException(EntryErrorCode.EVENT_NOT_OPEN);
		IllegalStateException recordingFailure = new IllegalStateException("db down");
		when(recorder.record(command)).thenThrow(rejection);
		org.mockito.Mockito.doThrow(recordingFailure).when(rejectionRecorder).record(command, rejection);
		// when
		// then
		assertThatThrownBy(() -> service.enter(command)).isSameAs(rejection)
				.hasSuppressedException(recordingFailure);
		verify(recorder, times(1)).record(command);
	}

	@Test
	@DisplayName("요청·사용자·이벤트·응모 ID가 없으면 처리하지 않고 요청 형식 오류다")
	void rejectsMissingIdentifiers() {
		// given
		// when
		// then
		for (EntryCommand invalid : new EntryCommand[] {
				null,
				new EntryCommand(null, false, Membership.VIP, EVENT_ID, ENTRY_ID, Map.of()),
				new EntryCommand(USER_ID, false, Membership.VIP, null, ENTRY_ID, Map.of()),
				new EntryCommand(USER_ID, false, Membership.VIP, EVENT_ID, null, Map.of())}) {
			assertThatThrownBy(() -> service.enter(invalid))
					.isInstanceOfSatisfying(EntryException.class,
							e -> assertThat(e.getErrorCode()).isEqualTo(EntryErrorCode.INVALID_ENTRY_REQUEST));
		}
		verify(recorder, never()).record(org.mockito.ArgumentMatchers.any());
		verifyNoInteractions(recorder, rejectionRecorder);
	}
}
