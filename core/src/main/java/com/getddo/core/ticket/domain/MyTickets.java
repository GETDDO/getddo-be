package com.getddo.core.ticket.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;

/** 내 응모권 보유 조회 결과(T01). 모든 값은 같은 {@code serverTime} 기준으로 사용 가능한 응모권만 센다. */
@Getter
public final class MyTickets {

	/** 사용 가능한 실제 장수. 등급과 관계없이 1장은 1로 센다. */
	private final long availableCount;
	/** 등급별 사용 가능한 장수. 장수가 0인 등급도 포함한다. */
	private final Map<TicketGrade, Long> countByGrade;
	/** 등급·만료 시각별 묶음. 만료가 임박한 순서다. */
	private final List<TicketHolding> holdings;
	private final Instant serverTime;

	private MyTickets(long availableCount, Map<TicketGrade, Long> countByGrade, List<TicketHolding> holdings,
			Instant serverTime) {
		this.availableCount = availableCount;
		this.countByGrade = Collections.unmodifiableMap(countByGrade);
		this.holdings = List.copyOf(holdings);
		this.serverTime = serverTime;
	}

	/** 조회 시각 기준 묶음 목록으로 결과를 만든다. 합계와 등급별 장수는 묶음의 장수를 더해 구한다. */
	public static MyTickets of(List<TicketHolding> holdings, Instant serverTime) {
		Map<TicketGrade, Long> countByGrade = new EnumMap<>(TicketGrade.class);
		for (TicketGrade grade : TicketGrade.values()) {
			countByGrade.put(grade, 0L);
		}
		long available = 0;
		for (TicketHolding holding : holdings) {
			countByGrade.merge(holding.getGrade(), holding.getCount(), Math::addExact);
			available = Math.addExact(available, holding.getCount());
		}
		return new MyTickets(available, countByGrade, holdings, serverTime);
	}
}
