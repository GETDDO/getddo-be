package com.getddo.core.entry.service;

import java.sql.SQLException;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
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
 *
 * <p>스펙이 이력으로 남기도록 한 업무 거절({@link EntryRejections})은 응모 트랜잭션이 롤백된 뒤 별도 트랜잭션
 * ({@link EntryRejectionRecorder})에서 기록하고 원래 예외를 그대로 던진다. 같은 응모 ID를 다시 요청하면 저장된 거절 코드로
 * 처음과 같은 오류를 돌려준다.</p>
 */
@Service
@RequiredArgsConstructor
public class EntryService {

	/** MySQL {@code ER_DUP_ENTRY}: UNIQUE·PK 중복 입력. */
	private static final int MYSQL_DUPLICATE_ENTRY = 1062;

	private final EntryRecorder recorder;
	private final EntryRejectionRecorder rejectionRecorder;

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
			return process(command);
		} catch (BusinessException failure) {
			if (rememberRejection(command, failure)) {
				throw failure;
			}
			// 같은 응모 ID의 결과가 동시에 먼저 확정됐다. 그 결과를 돌려주거나 같은 거절을 그대로 던진다.
			return process(command);
		}
	}

	/**
	 * 업무 거절을 별도 트랜잭션에서 이력으로 남긴다. 이력 저장이 실패해도 사용자에게는 원래 거절을 돌려주므로 실패는 원래
	 * 예외에 덧붙여 남기고 삼키지 않는다.
	 *
	 * @return 같은 응모 ID의 결과가 이미 있어(동시에 먼저 확정됐거나 이전 거절 기록) 다시 처리해야 하면 false
	 */
	private boolean rememberRejection(EntryCommand command, BusinessException failure) {
		try {
			return rejectionRecorder.record(command, failure);
		} catch (DataIntegrityViolationException violation) {
			if (isDuplicateKey(violation)) {
				return false;
			}
			failure.addSuppressed(violation);
			return true;
		} catch (RuntimeException recordingFailure) {
			failure.addSuppressed(recordingFailure);
			return true;
		}
	}

	/** 응모 결과를 한 번 처리하고, 동시 응모로 롤백되면 새 트랜잭션에서 한 번 다시 처리한다. */
	private EntryReceipt process(EntryCommand command) {
		try {
			return recorder.record(command);
		} catch (PessimisticLockingFailureException concurrentEntry) {
			// 동시 응모의 잠금 교착·대기 실패다. 롤백된 트랜잭션은 버리고 새 트랜잭션에서 한 번만 다시 처리한다.
			return recorder.record(command);
		} catch (DataIntegrityViolationException violation) {
			// 같은 사용자의 첫 응모나 같은 응모 ID가 동시에 겹친 UNIQUE 위반만 다시 처리한다. FK·NOT NULL·CHECK 위반은
			// 재처리해도 같으므로 잠금과 조회를 반복하지 않고 그대로 전파한다.
			if (!isDuplicateKey(violation)) {
				throw violation;
			}
			return recorder.record(command);
		}
	}

	/** 원인 사슬에 MySQL 중복 키 오류(1062)가 있는지 확인한다. */
	private static boolean isDuplicateKey(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sql.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
				return true;
			}
		}
		return false;
	}
}
