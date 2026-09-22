package com.getddo.core.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 주입받은 시계로 현재 시각을 조회하고 한국 기준 시각·업무 날짜·월을 계산한다.
 */
public final class TimeProvider {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private final Clock clock;

	/**
	 * 현재 시각 조회에 사용할 시계를 지정한다.
	 *
	 * @param clock 운영용 시스템 시계 또는 테스트용 고정 시계
	 * @throws NullPointerException clock이 null인 경우
	 */
	public TimeProvider(Clock clock) {
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	/**
	 * 주입받은 시계의 현재 순간을 반환한다.
	 *
	 * @return 지역 시간대와 무관한 현재 순간
	 */
	public Instant now() {
		return clock.instant();
	}

	/**
	 * 주어진 순간을 한국 시간대({@code Asia/Seoul})의 날짜·시간으로 표현한다.
	 *
	 * @param instant 변환할 순간
	 * @return 같은 순간을 나타내는 한국 날짜·시간
	 * @throws NullPointerException instant가 null인 경우
	 */
	public ZonedDateTime toKst(Instant instant) {
		return Objects.requireNonNull(instant, "instant").atZone(KST);
	}

	/**
	 * 시간대가 없는 날짜·시간을 한국 시각으로 해석해 해당 순간을 반환한다.
	 *
	 * <p>오프셋이 있는 입력은 오프셋을 제거하지 않고 해당 값의
	 * {@code toInstant()}로 변환한다.</p>
	 *
	 * @param kstDateTime 한국 시각으로 해석할 날짜·시간
	 * @return UTC 기준 저장·비교에 사용할 순간
	 * @throws NullPointerException kstDateTime이 null인 경우
	 */
	public Instant toUtc(LocalDateTime kstDateTime) {
		return Objects.requireNonNull(kstDateTime, "kstDateTime")
				.atZone(KST).toInstant();
	}

	/**
	 * 주어진 순간의 한국 기준 업무 날짜를 계산한다.
	 *
	 * @param instant 출석일·일일 보상 기준일 등을 판단할 순간
	 * @return 한국 시간대 기준의 날짜
	 * @throws NullPointerException instant가 null인 경우
	 */
	public LocalDate businessDate(Instant instant) {
		return toKst(instant).toLocalDate();
	}

	/**
	 * 주어진 순간의 한국 기준 업무 연·월을 계산한다.
	 *
	 * @param instant 월간 출석·응모권 귀속월 등을 판단할 순간
	 * @return 한국 시간대 기준의 연·월
	 * @throws NullPointerException instant가 null인 경우
	 */
	public YearMonth businessMonth(Instant instant) {
		return YearMonth.from(toKst(instant));
	}
}
