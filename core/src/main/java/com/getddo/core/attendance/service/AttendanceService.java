package com.getddo.core.attendance.service;

import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.attendance.domain.AttendanceReceipt;

/**
 * 출석(AT02) 진입점.
 *
 * <p>서버 KST 업무일 기준으로 하루 한 번 출석을 인정하고, 일일 보상과 연속 출석 단계 보상을 응모권으로 지급한다.
 * 출석 기록·보상 청구·응모권 지급은 한 트랜잭션에서 함께 성공하거나 함께 취소된다.</p>
 *
 * <p>출석은 독립된 업무 단위다. 호출자 트랜잭션이 있어도 잠시 멈추고({@code NOT_SUPPORTED}) 출석 처리 트랜잭션을 따로 열어
 * 커밋한다. 같은 날 동시 요청이 겹쳐 출석 UNIQUE 위반이 나거나 잠금 교착으로 롤백되면, 새 트랜잭션에서 한 번 더 처리해
 * 먼저 확정된 출석을 돌려준다. 호출자 트랜잭션에 합류하면 롤백 표시가 남아 이 재처리를 할 수 없기 때문이다.</p>
 */
@Service
public class AttendanceService {

	private final AttendanceRecorder recorder;

	public AttendanceService(AttendanceRecorder recorder) {
		this.recorder = recorder;
	}

	/**
	 * 요청 사용자를 오늘(KST) 출석 처리한다.
	 *
	 * <p>같은 날 다시 요청하면 새로 기록하거나 지급하지 않고 이미 확정한 결과를 {@code created=false}로 돌려준다.</p>
	 *
	 * @param userId 요청 사용자 ID. 호출자가 확인한 등록 사용자여야 한다
	 * @return 출석과 이 출석으로 확정된 보상
	 * @throws com.getddo.core.common.exception.BusinessException 적용할 출석 보상 정책이 없는 경우
	 *         {@code ATTENDANCE_POLICY_NOT_FOUND}
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public AttendanceReceipt attend(UUID userId) {
		Objects.requireNonNull(userId, "userId");
		try {
			return recorder.record(userId);
		} catch (DataIntegrityViolationException | PessimisticLockingFailureException concurrentAttendance) {
			// 같은 날 먼저 확정된 출석이 있거나, 월 첫 출석이 자정 전후로 겹쳐 잠금 교착이 났다.
			// 롤백된 트랜잭션은 버리고 새 트랜잭션에서 한 번만 다시 처리한다.
			// 다른 원인의 실패라면 재처리에서도 같은 예외가 나고 그대로 전파된다.
			return recorder.record(userId);
		}
	}
}
