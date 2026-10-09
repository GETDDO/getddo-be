package com.getddo.db.ticket.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantedTicket;
import com.getddo.core.ticket.domain.Ticket;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.repository.TicketRepository;
import com.getddo.db.ticket.entity.TicketEntity;
import com.getddo.db.ticket.entity.TicketHistoryEntity;
import com.getddo.db.ticket.mapper.TicketMapper;

@Repository
@RequiredArgsConstructor
public class TicketRepositoryImpl implements TicketRepository {

	private final TicketJpaRepository ticketJpaRepository;
	private final TicketHistoryJpaRepository historyJpaRepository;
	private final TicketMapper ticketMapper;

	/**
	 * {@inheritDoc}
	 *
	 * <p>응모권의 등급과 지급 이력의 처리·만료 시각을 합쳐 돌려준다. 만료 시각은 응모권의 현재 값이 아니라 지급 이력의
	 * 값이라 이후 반환으로 응모권이 바뀌어도 지급 당시 결과가 유지된다.</p>
	 */
	@Override
	public List<GrantedTicket> findGranted(GrantSource source) {
		List<TicketEntity> tickets = findBySource(source);
		if (tickets.isEmpty()) {
			return List.of();
		}
		Map<UUID, TicketHistoryEntity> grants = historyJpaRepository
				.findByTicketIdInAndOperationType(tickets.stream().map(TicketEntity::getId).toList(),
						TicketOperationType.GRANT)
				.stream()
				.collect(Collectors.toMap(TicketHistoryEntity::getTicketId, Function.identity()));
		return tickets.stream()
				.map(ticket -> toGranted(ticket, grants.get(ticket.getId())))
				.toList();
	}

	@Override
	public List<Ticket> saveAll(List<Ticket> tickets) {
		List<TicketEntity> entities = tickets.stream().map(ticketMapper::toEntity).toList();
		return ticketJpaRepository.saveAll(entities).stream().map(ticketMapper::toDomain).toList();
	}

	private List<TicketEntity> findBySource(GrantSource source) {
		return switch (source.getType()) {
			case MISSION -> ticketJpaRepository.findByMissionRewardClaimId(source.getClaimId());
			case ATTENDANCE -> ticketJpaRepository.findByAttendanceRewardClaimId(source.getClaimId());
			case GAME -> ticketJpaRepository.findByGameRewardClaimId(source.getClaimId());
		};
	}

	private static GrantedTicket toGranted(TicketEntity ticket, TicketHistoryEntity grant) {
		if (grant == null) {
			throw new IllegalStateException("응모권에 지급 이력이 없다.");
		}
		return new GrantedTicket(ticket.getId(), ticket.getGrade(), grant.getCreatedAt(), grant.getExpiresAt());
	}
}
