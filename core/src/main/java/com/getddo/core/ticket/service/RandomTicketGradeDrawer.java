package com.getddo.core.ticket.service;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Component;

import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.domain.TicketGradeDrawer;

/**
 * 확률로 응모권 등급을 정하는 기본 구현.
 *
 * <p>출석은 브론즈로 고정한다. 미션·게임은 0~99 사이 정수를 하나 뽑아 브론즈 80%, 실버 18%, 골드 2%로 나눈다.
 * 개별 사용자나 일정 횟수 안에서 등급 비율을 맞추지 않는 단순 무작위다.</p>
 */
@Component
public class RandomTicketGradeDrawer implements TicketGradeDrawer {

	private static final int TOTAL = 100;
	private static final int BRONZE_UPPER_BOUND = 80;
	private static final int SILVER_UPPER_BOUND = 98;

	private final RandomGenerator random;

	public RandomTicketGradeDrawer() {
		this(new SecureRandom());
	}

	RandomTicketGradeDrawer(RandomGenerator random) {
		this.random = random;
	}

	@Override
	public TicketGrade draw(GrantSourceType type) {
		if (type == GrantSourceType.ATTENDANCE) {
			return TicketGrade.BRONZE;
		}
		int roll = random.nextInt(TOTAL);
		if (roll < BRONZE_UPPER_BOUND) {
			return TicketGrade.BRONZE;
		}
		if (roll < SILVER_UPPER_BOUND) {
			return TicketGrade.SILVER;
		}
		return TicketGrade.GOLD;
	}
}
