package com.getddo.core.entry.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.entry.domain.EntryParticipant;
import com.getddo.core.entry.domain.EntryParticipationTotals;
import com.getddo.core.entry.domain.EntryStatistics;
import com.getddo.core.entry.exception.EntryErrorCode;
import com.getddo.core.entry.exception.EntryException;
import com.getddo.core.entry.repository.EntryEventReader;
import com.getddo.core.entry.repository.EntryParticipantRepository;

/**
 * 이벤트 응모 현황 조회(E03). 아무것도 잠그지 않고 읽기만 한다.
 *
 * <p>합계는 접수 완료된 응모자 기준이다. 거절 이력과 반환은 반영하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class EntryStatisticsService {

	private final EntryEventReader eventReader;
	private final EntryParticipantRepository participantRepository;
	private final TimeProvider timeProvider;

	/**
	 * 이벤트의 응모 현황과 요청 사용자의 본인 차감 수량을 조회한다.
	 *
	 * @throws EntryException 이벤트가 없거나 삭제된 경우({@code EVENT_NOT_FOUND})
	 */
	@Transactional(readOnly = true)
	public EntryStatistics getStatistics(UUID userId, UUID eventId) {
		eventReader.find(eventId)
				.filter(found -> !found.isDeleted())
				.orElseThrow(() -> new EntryException(EntryErrorCode.EVENT_NOT_FOUND));
		EntryParticipationTotals totals = participantRepository.summarize(eventId);
		long mine = participantRepository.find(eventId, userId).map(EntryParticipant::getUsedTicketCount).orElse(0L);
		return new EntryStatistics(eventId, totals.getParticipantCount(), totals.getSpentTicketCount(), mine,
				timeProvider.now());
	}
}
