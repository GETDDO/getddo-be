package com.getddo.core.attendance.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 출석 날짜를 하루도 빠지지 않은 연속 구간으로 나눈다. 연속 일수와 단계 도달일을 출석 기록에서 계산하는 데 쓴다.
 */
final class ConsecutiveRuns {

	private ConsecutiveRuns() {
	}

	/** 연속 구간 하나. 시작일과 길이(일)다. */
	static final class Run {

		private final LocalDate start;
		private final int length;

		Run(LocalDate start, int length) {
			this.start = start;
			this.length = length;
		}

		LocalDate start() {
			return start;
		}

		int length() {
			return length;
		}
	}

	/**
	 * @param sortedDates 날짜 오름차순의 출석 날짜. 같은 날짜가 겹치면 한 번만 센다
	 * @return 시간순 연속 구간 목록. 날짜가 없으면 빈 목록
	 */
	static List<Run> of(List<LocalDate> sortedDates) {
		List<Run> runs = new ArrayList<>();
		LocalDate start = null;
		LocalDate previous = null;
		int length = 0;
		for (LocalDate date : sortedDates) {
			if (previous != null && date.equals(previous)) {
				continue;
			}
			if (previous != null && date.equals(previous.plusDays(1))) {
				length++;
			} else {
				if (start != null) {
					runs.add(new Run(start, length));
				}
				start = date;
				length = 1;
			}
			previous = date;
		}
		if (start != null) {
			runs.add(new Run(start, length));
		}
		return runs;
	}
}
