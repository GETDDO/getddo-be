package com.getddo.core.entry.service;

import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.entry.domain.Entry;
import com.getddo.core.entry.domain.EntryCommand;
import com.getddo.core.entry.domain.EntryEvent;
import com.getddo.core.entry.repository.EntryEventReader;
import com.getddo.core.entry.repository.EntryRepository;
import com.getddo.core.ticket.domain.TicketGrade;

/**
 * 업무 규칙으로 거절된 응모를 이력으로 남긴다. {@link EntryService}만 호출한다.
 *
 * <p>거절은 응모 처리 트랜잭션을 롤백시키므로 그 안에서 기록할 수 없다. 응모 트랜잭션이 끝난 뒤 독립된 새 트랜잭션
 * ({@code REQUIRES_NEW})에서 기록한다. 같은 응모 ID의 결과가 이미 있거나 이벤트가 없으면 아무것도 남기지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class EntryRejectionRecorder {

	private final EntryRepository entryRepository;
	private final EntryEventReader eventReader;
	private final TimeProvider timeProvider;

	/**
	 * 거절 이력을 남긴다. 이력으로 남기는 거절이 아니거나 요청 형식이 이벤트 규칙에 맞지 않으면 아무것도 남기지 않는다.
	 * 기간·관리자·멤버십 거절은 이벤트별 요청 검증보다 먼저 일어나므로 같은 검증을 여기서 한 번 더 한다.
	 *
	 * <p>같은 응모 ID의 결과가 동시에 먼저 저장되면 PK 위반 예외가 난다. 호출자가 그 결과를 다시 읽어 처리한다.</p>
	 *
	 * @return 같은 응모 ID의 결과가 이미 있어 호출자가 요청을 다시 처리해야 하면 false, 그렇지 않으면 true
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean record(EntryCommand command, BusinessException failure) {
		if (!EntryRejections.isRecordable(failure)) {
			return true;
		}
		if (entryRepository.findById(command.getEntryId()).isPresent()) {
			return false;
		}
		// 이력은 이벤트 FK가 필요하다. 없거나 삭제된 이벤트에는 남기지 않는다.
		Optional<EntryEvent> event = eventReader.find(command.getEventId()).filter(found -> !found.isDeleted());
		if (event.isEmpty()) {
			return true;
		}
		Map<TicketGrade, Long> tickets = EntryRecorder.normalize(command.getTickets());
		if (!EntryRecorder.isValidRequest(event.get(), tickets)) {
			return true;
		}
		entryRepository.save(Entry.rejected(command.getEntryId(), command.getEventId(), command.getUserId(), tickets,
				failure.getErrorCode(), timeProvider.now().truncatedTo(ChronoUnit.MICROS)));
		return true;
	}
}
