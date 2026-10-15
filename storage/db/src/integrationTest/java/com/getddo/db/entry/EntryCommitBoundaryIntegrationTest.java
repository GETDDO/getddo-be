package com.getddo.db.entry;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.getddo.core.entry.domain.EntryReceipt;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.repository.EntryParticipantRepository;
import com.getddo.core.ticket.domain.TicketGrade;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * 응모의 마지막 서비스 검사와 실제 커밋 사이에 마감 시각을 넘는 경우를 실제 MySQL로 검증한다.
 *
 * <p>차감 직후 같은 트랜잭션에 커밋 직전 동작을 등록해 시계를 옮긴다. 서비스의 마지막 마감 확인은 이미 지난 뒤라서, 커밋 직전
 * 재확인이 없으면 마감 뒤에 확정된 응모가 남는다.</p>
 */
class EntryCommitBoundaryIntegrationTest extends EntryIntegrationTestSupport {

	/** {@code weightedEvent}의 마감 시각. 이 시각 정각부터는 응모할 수 없다. */
	private static final Instant END = NOW.plusSeconds(86_400);

	@MockitoSpyBean
	private EntryParticipantRepository participantRepository;

	/** 응모자 누적 수량을 늘린 직후, 커밋 직전에 시계를 {@code at}으로 옮기도록 같은 트랜잭션에 등록한다. */
	private void moveClockJustBeforeCommit(Instant at) {
		doAnswer(invocation -> {
			invocation.callRealMethod();
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void beforeCommit(boolean readOnly) {
					clock.set(at);
				}
			});
			return null;
		}).when(participantRepository).addUsedTicketCount(any(UUID.class), anyLong());
	}

	@Test
	@DisplayName("마지막 서비스 검사 뒤 커밋 직전에 마감을 넘기면 ENTRY-005로 롤백하고 응모·차감은 남지 않으며 거절 이력만 남는다")
	void deadlinePassingBeforeCommitRollsBackAndKeepsOnlyRejection() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 1);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		moveClockJustBeforeCommit(END);
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry, Map.of(TicketGrade.GOLD, 1L))))
				.satisfies(failure -> assertEntryError(failure, EntryErrorCode.EVENT_NOT_OPEN));
		assertThat(count("select count(*) from event_entries where id = ? and status = 'ACCEPTED'", bytes(entry)))
				.as("커밋 직전에 마감된 요청의 ACCEPTED 잔존").isZero();
		assertThat(count("select count(*) from event_entries where id = ? and status = 'REJECTED' "
				+ "and rejection_code = 'ENTRY-005'", bytes(entry))).isEqualTo(1);
		assertThat(count("select count(*) from event_participants where user_id = ?", bytes(userId))).isZero();
		assertThat(spentTickets(userId)).isZero();
		assertThat(count("select count(*) from ticket_histories where event_entry_id = ?", bytes(entry))).isZero();
	}

	@Test
	@DisplayName("커밋 직전 시각이 마감 1마이크로초 전이면 정상 응모로 확정된다")
	void oneMicrosecondBeforeDeadlineAtCommitIsAccepted() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 1);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		moveClockJustBeforeCommit(END.minusNanos(1_000));
		// when
		EntryReceipt receipt = entryService.enter(command(userId, event, entry, Map.of(TicketGrade.GOLD, 1L)));
		// then
		assertThat(receipt.isCreated()).isTrue();
		assertThat(count("select count(*) from event_entries where id = ? and status = 'ACCEPTED'", bytes(entry)))
				.isEqualTo(1);
		assertThat(usedTicketCount(userId, event)).isEqualTo(1);
		assertThat(spentTickets(userId)).isEqualTo(1);
	}

	@Test
	@DisplayName("커밋 직전에 마감을 넘겨 거절된 응모 ID를 다시 보내도 처음과 같은 ENTRY-005를 돌려준다")
	void replayOfBoundaryRejectionReturnsSameError() {
		// given
		giveTickets(userId, TicketGrade.GOLD, 1);
		UUID event = weightedEvent(5);
		UUID entry = UUID.randomUUID();
		moveClockJustBeforeCommit(END);
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry, Map.of(TicketGrade.GOLD, 1L))))
				.satisfies(failure -> assertEntryError(failure, EntryErrorCode.EVENT_NOT_OPEN));
		// when
		// then
		assertThatThrownBy(() -> entryService.enter(command(userId, event, entry, Map.of(TicketGrade.GOLD, 1L))))
				.satisfies(failure -> assertEntryError(failure, EntryErrorCode.EVENT_NOT_OPEN));
		assertThat(count("select count(*) from event_entries where id = ?", bytes(entry))).isEqualTo(1);
		assertThat(spentTickets(userId)).isZero();
	}
}
