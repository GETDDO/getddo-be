package com.getddo.core.mission.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionDetailView;
import com.getddo.core.mission.domain.MissionSearch;
import com.getddo.core.mission.domain.MissionSummary;
import com.getddo.core.mission.domain.MissionType;
import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;
import com.getddo.core.mission.repository.MissionRepository;
import com.getddo.core.mission.repository.MissionSubmissionRepository;

/**
 * 사용자의 미션 목록(M01)과 상세(M02) 조회다. 읽기 전용이며 아무것도 잠그지 않는다.
 *
 * <p>목록은 운영 상태·기간과 관계없이 모든 미션을 시작 시각 내림차순으로 보여 주고, 진행 여부는 응답의 기간으로 판단한다.
 * 완료 표시는 사용자가 완료한 제출이 있는지로 정한다.</p>
 */
@Service
@RequiredArgsConstructor
public class MissionQueryService {

	private final MissionRepository missionRepository;
	private final MissionSubmissionRepository submissionRepository;

	/**
	 * 미션 목록을 사용자의 완료 표시와 함께 조회한다. 완료 표시는 페이지의 미션에 대해 한 번에 조회한다.
	 *
	 * @param userId      요청한 사용자
	 * @param missionType 지정하면 해당 유형만 조회한다. 없으면 모두 조회한다
	 * @param page        페이지 조건
	 * @return 시작 시각 내림차순(같으면 ID 내림차순)의 미션 요약
	 */
	@Transactional(readOnly = true)
	public PageResult<MissionSummary> findMissions(UUID userId, MissionType missionType, PageQuery page) {
		PageResult<Mission> missions = missionRepository.search(new MissionSearch(missionType, null, null), page);
		List<UUID> missionIds = missions.getItems().stream().map(Mission::getId).toList();
		Set<UUID> completed = missionIds.isEmpty() ? Set.of()
				: submissionRepository.findCompletedMissionIds(userId, missionIds);
		return missions.map(mission -> new MissionSummary(mission, completed.contains(mission.getId())));
	}

	/**
	 * 미션 한 건을 문항·선택지와 함께 조회한다.
	 *
	 * @param userId    요청한 사용자
	 * @param missionId 미션 ID
	 * @return 문항·선택지를 포함한 미션과 사용자의 완료 여부
	 * @throws MissionException 미션이 없는 경우
	 */
	@Transactional(readOnly = true)
	public MissionDetailView findMission(UUID userId, UUID missionId) {
		MissionDetail detail = missionRepository.findDetailById(missionId)
				.orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND));
		boolean completed = submissionRepository.findCompleted(userId, missionId).isPresent();
		return new MissionDetailView(detail, completed);
	}
}
