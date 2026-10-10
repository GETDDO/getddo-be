package com.getddo.db.ticket.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantedTicket;
import com.getddo.core.ticket.domain.Ticket;
import com.getddo.core.ticket.domain.TicketOperationType;
import com.getddo.core.ticket.repository.TicketRepository;
import com.getddo.db.common.util.UuidBinary;
import com.getddo.db.ticket.entity.TicketEntity;
import com.getddo.db.ticket.entity.TicketHistoryEntity;
import com.getddo.db.ticket.mapper.TicketMapper;

@Repository
@RequiredArgsConstructor
public class TicketRepositoryImpl implements TicketRepository {

	private final TicketJpaRepository ticketJpaRepository;
	private final TicketHistoryJpaRepository historyJpaRepository;
	private final TicketMapper ticketMapper;
	private final EntityManager entityManager;

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

	/**
	 * {@inheritDoc}
	 *
	 * <p>후보 ID는 잠그지 않고 읽은 뒤 선택 순서대로 PK로 한 장씩 잠근다. 잠금을 기다리는 동안 다른 처리가 바꾼 응모권은
	 * 잠근 뒤 최신 값으로 다시 판정해 건너뛴다. 범위에 {@code FOR UPDATE}를 걸지 않아 gap lock이 생기지 않는다.</p>
	 */
	@Override
	public List<Ticket> findUsableForUpdate(UUID userId, Instant now, int limit) {
		List<Ticket> locked = new ArrayList<>();
		for (byte[] raw : ticketJpaRepository.findUsableIds(UuidBinary.toBytes(userId), now)) {
			if (locked.size() >= limit) {
				break;
			}
			lockAndRefresh(UuidBinary.fromBytes(raw))
					.filter(entity -> entity.getStatus().isUsable() && entity.getExpiresAt().isAfter(now))
					.ifPresent(entity -> locked.add(ticketMapper.toDomain(entity)));
		}
		return locked;
	}

	/** {@inheritDoc} PK 바이트 순서(DB 정렬과 같음)로 한 장씩 잠근다. */
	@Override
	public List<Ticket> findAllForUpdate(Collection<UUID> ids) {
		return ids.stream()
				.distinct()
				.sorted((left, right) -> Arrays.compareUnsigned(UuidBinary.toBytes(left), UuidBinary.toBytes(right)))
				.map(this::lockAndRefresh)
				.flatMap(Optional::stream)
				.map(ticketMapper::toDomain)
				.toList();
	}

	/**
	 * 응모권 행을 PK로 잠그고 영속성 컨텍스트의 값을 DB 최신 값으로 덮어쓴다. 호출자가 같은 트랜잭션에서 이미 읽어 둔
	 * Entity가 있어도 오래된 값으로 판정하지 않기 위해서다. 덮어쓰기 전에 flush해, 같은 트랜잭션에서 바꾸고 아직 DB에
	 * 쓰지 않은 변경이 사라지지 않게 한다.
	 */
	private Optional<TicketEntity> lockAndRefresh(UUID id) {
		entityManager.flush();
		TicketEntity entity = entityManager.getReference(TicketEntity.class, id);
		try {
			entityManager.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
		} catch (EntityNotFoundException e) {
			return Optional.empty();
		}
		return Optional.of(entity);
	}

	@Override
	public List<Ticket> findAllByIds(Collection<UUID> ids) {
		return ticketJpaRepository.findAllById(ids).stream().map(ticketMapper::toDomain).toList();
	}

	/** 영속성 컨텍스트의 Entity에 바뀐 값만 반영하며, 커밋 때 UPDATE로 나간다. */
	@Override
	public void updateAll(List<Ticket> tickets) {
		Map<UUID, TicketEntity> entities = ticketJpaRepository
				.findAllById(tickets.stream().map(Ticket::getId).toList()).stream()
				.collect(Collectors.toMap(TicketEntity::getId, Function.identity()));
		for (Ticket ticket : tickets) {
			TicketEntity entity = entities.get(ticket.getId());
			if (entity == null) {
				throw new IllegalStateException("갱신할 응모권이 없다.");
			}
			entity.applyTransition(ticket.getStatus(), ticket.getExpiresAt(), ticket.getVersion(),
					ticket.getUpdatedAt());
		}
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
