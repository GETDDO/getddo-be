package com.getddo.core.mission.service;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.mission.domain.MissionSubmissionResult;
import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;
import com.getddo.core.mission.repository.MissionRepository;
import com.getddo.core.mission.repository.MissionSubmissionRepository;

/**
 * 사용자의 내 미션 제출 내역 조회(M04)다. 읽기 전용이며 아무것도 잠그지 않는다.
 *
 * <p>오답 제출도 정상 결과({@code isCompleted=false}, 보상 없음)로 보여 주고, 완료한 제출에는 그 제출의 보상 청구로 확정된
 * 응모권 지급 결과를 담는다. 정답은 결과에 포함하지 않는다.</p>
 */
@Service
@RequiredArgsConstructor
public class MissionSubmissionQueryService {

	private final MissionRepository missionRepository;
	private final MissionSubmissionRepository submissionRepository;
	private final MissionSubmissionResultReader resultReader;

	/**
	 * 사용자가 해당 미션에 낸 제출을 최신순(같으면 ID 내림차순)으로 조회한다.
	 *
	 * @param userId    요청한 사용자
	 * @param missionId 미션 ID
	 * @param page      페이지 조건
	 * @return 제출 결과. 제출이 없으면 빈 페이지다
	 * @throws MissionException 미션이 없는 경우
	 */
	@Transactional(readOnly = true)
	public PageResult<MissionSubmissionResult> findMySubmissions(UUID userId, UUID missionId, PageQuery page) {
		missionRepository.findById(missionId)
				.orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND));
		return submissionRepository.findByUser(userId, missionId, page)
				.map(submission -> resultReader.read(submission, false));
	}
}
