package com.getddo.core.attendance.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;

import com.getddo.core.attendance.domain.AttendanceReceipt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final AttendanceReceipt RECEIPT = new AttendanceReceipt(UUID.randomUUID(),
			LocalDate.parse("2026-09-07"), 1, List.of(), Instant.parse("2026-09-07T03:00:00Z"), true);

	@Mock
	private AttendanceRecorder recorder;

	@Test
	@DisplayName("한 번에 처리되면 그 결과를 돌려준다")
	void returnsRecordedReceipt() {
		// given
		when(recorder.record(USER_ID)).thenReturn(RECEIPT);
		// when
		AttendanceReceipt receipt = new AttendanceService(recorder).attend(USER_ID);
		// then
		assertThat(receipt).isEqualTo(RECEIPT);
		verify(recorder, times(1)).record(USER_ID);
	}

	@Test
	@DisplayName("동시 요청으로 UNIQUE 위반이 나면 새 트랜잭션에서 한 번 더 처리해 먼저 확정된 출석을 돌려준다")
	void retriesOnceAfterUniqueViolation() {
		// given
		AttendanceReceipt existing = new AttendanceReceipt(RECEIPT.getAttendanceId(), RECEIPT.getAttendanceDate(), 1,
				List.of(), RECEIPT.getCreatedAt(), false);
		when(recorder.record(USER_ID))
				.thenThrow(new DataIntegrityViolationException("uq_attendances_1"))
				.thenReturn(existing);
		// when
		AttendanceReceipt receipt = new AttendanceService(recorder).attend(USER_ID);
		// then
		assertThat(receipt).isEqualTo(existing);
		verify(recorder, times(2)).record(USER_ID);
	}

	@Test
	@DisplayName("교착 등 잠금 실패로 롤백되어도 새 트랜잭션에서 한 번 더 처리한다")
	void retriesOnceAfterLockFailure() {
		// given
		when(recorder.record(USER_ID))
				.thenThrow(new DeadlockLoserDataAccessException("deadlock", null))
				.thenReturn(RECEIPT);
		// when
		AttendanceReceipt receipt = new AttendanceService(recorder).attend(USER_ID);
		// then
		assertThat(receipt).isEqualTo(RECEIPT);
		verify(recorder, times(2)).record(USER_ID);
	}

	@Test
	@DisplayName("재처리에서도 실패하면 더 반복하지 않고 예외를 전파한다")
	void propagatesSecondFailure() {
		// given
		DataIntegrityViolationException second = new DataIntegrityViolationException("second");
		when(recorder.record(USER_ID))
				.thenThrow(new DataIntegrityViolationException("first"))
				.thenThrow(second);
		// when
		// then
		assertThatThrownBy(() -> new AttendanceService(recorder).attend(USER_ID)).isSameAs(second);
		verify(recorder, times(2)).record(USER_ID);
	}

	@Test
	@DisplayName("사용자 ID가 없으면 처리하지 않는다")
	void rejectsNullUser() {
		// given
		// when
		// then
		assertThatNullPointerException().isThrownBy(() -> new AttendanceService(recorder).attend(null));
		verifyNoInteractions(recorder);
	}
}
