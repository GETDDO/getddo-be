package com.getddo.core.entry.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.entry.domain.EntryCommand;
import com.getddo.core.entry.domain.EntryReceipt;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;

/**
 * 이벤트 응모(E04) 진입점.
 *
 * <p>응모는 독립된 업무 단위다. 호출자 트랜잭션이 있어도 잠시 멈추고({@code NOT_SUPPORTED}) 응모 처리 트랜잭션을 따로 열어
 * 커밋한다. 같은 사용자의 첫 응모가 동시에 겹치거나 같은 응모 ID가 동시에 오면 응모자·응모 저장의 UNIQUE 위반이나 잠금
 * 교착으로 롤백되므로, 새 트랜잭션에서 한 번 더 처리해 먼저 확정된 결과를 반환하거나 이어서 응모한다. 호출자 트랜잭션에
 * 합류하면 롤백 표시가 남아 이 재처리를 할 수 없다.</p>
 */
@Service
@RequiredArgsConstructor
public class EntryService {

	private final EntryRecorder recorder;

	/**
	 * 요청 사용자가 이벤트에 응모한다.
	 *
	 * <p>같은 응모 ID로 다시 요청하면 새로 응모하지 않고 이미 접수한 결과를 {@code created=false}로 돌려준다. 응모 ID는
	 * 클라이언트가 요청 전에 정한 UUID({@code Idempotency-Key})다.</p>
	 *
	 * @throws EntryException 요청 값이 없거나, 응모 규칙을 어긴 경우
	 * @throws com.getddo.core.ticket.exception.TicketException 등급별 사용 가능한 응모권이 부족한 경우
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public EntryReceipt enter(EntryCommand command) {
		if (command == null || command.getUserId() == null || command.getEventId() == null
				|| command.getEntryId() == null) {
			throw new EntryException(EntryErrorCode.INVALID_ENTRY_REQUEST);
		}
		try {
			return recorder.record(command);
		} catch (DataIntegrityViolationException | PessimisticLockingFailureException concurrentEntry) {
			// 같은 사용자의 첫 응모나 같은 응모 ID가 동시에 겹쳤다. 롤백된 트랜잭션은 버리고 새 트랜잭션에서 한 번만 다시 처리한다.
			// 다른 원인의 실패라면 재처리에서도 같은 예외가 나고 그대로 전파된다.
			return recorder.record(command);
		}
	}
}
