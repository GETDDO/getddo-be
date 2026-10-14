package com.getddo.core.attendance.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;

import com.getddo.core.attendance.domain.AttendanceReceipt;
import com.getddo.core.common.time.TimeProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	/** 2026-09-07 12:00 KST. 저장 정밀도보다 긴 나노초를 둔다. */
	private static final Instant NOW = Instant.parse("2026-09-07T03:00:00.123456789Z");
	private static final Instant REQUESTED_AT = Instant.parse("2026-09-07T03:00:00.123456Z");
	private static final AttendanceReceipt RECEIPT = new AttendanceReceipt(UUID.randomUUID(),
			LocalDate.parse("2026-09-07"), 1, List.of(), Instant.parse("2026-09-07T03:00:00Z"), true);

	@Mock
	private AttendanceRecorder recorder;

	private AttendanceService service;

	@BeforeEach
	void setUp() {
		service = new AttendanceService(recorder, new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)));
	}

	@Test
	@DisplayName("한 번에 처리되면 그 결과를 돌려준다. 요청 시각은 마이크로초로 잘라 넘긴다")
	void returnsRecordedReceipt() {
		// given
		when(recorder.record(USER_ID, REQUESTED_AT)).thenReturn(RECEIPT);
		// when
		AttendanceReceipt receipt = service.attend(USER_ID);
		// then
		assertThat(receipt).isEqualTo(RECEIPT);
		verify(recorder, times(1)).record(USER_ID, REQUESTED_AT);
	}

	@Test
	@DisplayName("동시 요청으로 UNIQUE 위반이 나면 새 트랜잭션에서 한 번 더 처리해 먼저 확정된 출석을 돌려준다")
	void retriesOnceAfterUniqueViolation() {
		// given
		AttendanceReceipt existing = new AttendanceReceipt(RECEIPT.getAttendanceId(), RECEIPT.getAttendanceDate(), 1,
				List.of(), RECEIPT.getCreatedAt(), false);
		when(recorder.record(USER_ID, REQUESTED_AT))
				.thenThrow(new DataIntegrityViolationException("uq_attendances_1"))
				.thenReturn(existing);
		// when
		AttendanceReceipt receipt = service.attend(USER_ID);
		// then
		assertThat(receipt).isEqualTo(existing);
		verify(recorder, times(2)).record(USER_ID, REQUESTED_AT);
	}

	@Test
	@DisplayName("잠금을 기다리는 사이 KST 자정이 지나도 재처리는 첫 요청 시각으로 처리해 다음 날 출석을 만들지 않는다")
	void retryKeepsFirstRequestTimeAcrossMidnight() {
		// given: 첫 시계 조회는 9/7 23:59:59.999999 KST, 그 뒤 조회는 9/8 00:00:01 KST
		Instant beforeMidnight = Instant.parse("2026-09-07T14:59:59.999999Z");
		Instant afterMidnight = Instant.parse("2026-09-07T15:00:01Z");
		AttendanceService midnightService = new AttendanceService(recorder,
				new TimeProvider(new SequenceClock(beforeMidnight, afterMidnight)));
		AttendanceReceipt existing = new AttendanceReceipt(UUID.randomUUID(), LocalDate.parse("2026-09-07"), 1,
				List.of(), beforeMidnight, false);
		when(recorder.record(USER_ID, beforeMidnight))
				.thenThrow(new DataIntegrityViolationException("uq_attendances_1"))
				.thenReturn(existing);
		// when
		AttendanceReceipt receipt = midnightService.attend(USER_ID);
		// then
		assertThat(receipt).isEqualTo(existing);
		verify(recorder, times(2)).record(USER_ID, beforeMidnight);
		verify(recorder, never()).record(USER_ID, afterMidnight);
	}

	@Test
	@DisplayName("교착 등 잠금 실패로 롤백되어도 새 트랜잭션에서 한 번 더 처리한다")
	void retriesOnceAfterLockFailure() {
		// given
		when(recorder.record(USER_ID, REQUESTED_AT))
				.thenThrow(new DeadlockLoserDataAccessException("deadlock", null))
				.thenReturn(RECEIPT);
		// when
		AttendanceReceipt receipt = service.attend(USER_ID);
		// then
		assertThat(receipt).isEqualTo(RECEIPT);
		verify(recorder, times(2)).record(USER_ID, REQUESTED_AT);
	}

	@Test
	@DisplayName("재처리에서도 실패하면 더 반복하지 않고 예외를 전파한다")
	void propagatesSecondFailure() {
		// given
		DataIntegrityViolationException second = new DataIntegrityViolationException("second");
		when(recorder.record(USER_ID, REQUESTED_AT))
				.thenThrow(new DataIntegrityViolationException("first"))
				.thenThrow(second);
		// when
		// then
		assertThatThrownBy(() -> service.attend(USER_ID)).isSameAs(second);
		verify(recorder, times(2)).record(USER_ID, REQUESTED_AT);
	}

	@Test
	@DisplayName("사용자 ID가 없으면 처리하지 않는다")
	void rejectsNullUser() {
		// given
		// when
		// then
		assertThatNullPointerException().isThrownBy(() -> service.attend(null));
		verifyNoInteractions(recorder);
	}

	/** 조회할 때마다 다음 시각을 돌려주고, 마지막 시각에서 멈추는 시계. */
	private static final class SequenceClock extends Clock {

		private final Instant[] instants;
		private final AtomicInteger reads = new AtomicInteger();

		private SequenceClock(Instant... instants) {
			this.instants = instants;
		}

		@Override
		public Instant instant() {
			return instants[Math.min(reads.getAndIncrement(), instants.length - 1)];
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
