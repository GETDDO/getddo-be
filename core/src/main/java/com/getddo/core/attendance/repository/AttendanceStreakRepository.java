package com.getddo.core.attendance.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import com.getddo.core.attendance.domain.AttendanceStreak;

/** 월별 연속 출석 현황 저장소. 모든 메서드는 트랜잭션 안에서 호출한다. */
public interface AttendanceStreakRepository {

	/**
	 * 사용자의 해당 월 현황을 잠가 조회한다.
	 *
	 * <p>자정 전후 요청처럼 날짜가 다른 출석이 같은 달 현황을 동시에 갱신하지 못하게 한다.</p>
	 *
	 * @param userId      사용자 ID
	 * @param streakMonth KST 기준월의 1일
	 * @return 잠긴 현황. 없으면 빈 값
	 */
	Optional<AttendanceStreak> findForUpdate(UUID userId, LocalDate streakMonth);

	/**
	 * 사용자의 해당 월 현황을 잠그지 않고 조회한다. 이미 확정된 출석의 결과를 돌려줄 때처럼 읽기만 할 때 쓴다.
	 *
	 * @param userId      사용자 ID
	 * @param streakMonth KST 기준월의 1일
	 * @return 현황. 없으면 빈 값
	 */
	Optional<AttendanceStreak> find(UUID userId, LocalDate streakMonth);

	/**
	 * 현황을 저장한다. ID가 없으면 새로 만들고, 있으면 {@link #findForUpdate}로 잠근 현황을 갱신한다.
	 *
	 * @param streak 저장할 현황
	 * @return 저장된 현황
	 */
	AttendanceStreak save(AttendanceStreak streak);
}
