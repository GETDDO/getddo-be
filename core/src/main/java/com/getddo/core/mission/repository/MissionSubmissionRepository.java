package com.getddo.core.mission.repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.mission.domain.MissionSubmission;

public interface MissionSubmissionRepository {

	/**
	 * 제출과 답변을 함께 저장한다. 서버가 제출과 답변의 ID를 만든다.
	 *
	 * @param submission ID가 없는 제출
	 * @return ID가 채워진 제출. 답변도 ID를 가진다
	 */
	MissionSubmission insert(MissionSubmission submission);

	/**
	 * 제출 한 건을 답변과 함께 조회한다.
	 *
	 * @param submissionId 제출 ID
	 * @return 없으면 비어 있다
	 */
	Optional<MissionSubmission> findById(UUID submissionId);

	/**
	 * 사용자가 해당 미션을 완료한 제출을 조회한다. 완료는 한 번만 인정하므로 최대 한 건이다.
	 *
	 * @param userId    사용자 ID
	 * @param missionId 미션 ID
	 * @return 완료한 제출이 없으면 비어 있다
	 */
	Optional<MissionSubmission> findCompleted(UUID userId, UUID missionId);

	/**
	 * 사용자의 제출을 최신순(같으면 ID 내림차순)으로 조회한다.
	 *
	 * @param userId    사용자 ID
	 * @param missionId 미션 ID
	 * @param page      페이지 조건
	 * @return 답변을 포함한 제출 목록
	 */
	PageResult<MissionSubmission> findByUser(UUID userId, UUID missionId, PageQuery page);

	/**
	 * 주어진 미션 중 사용자가 완료한 미션의 ID를 조회한다. 목록 화면의 완료 표시에 쓴다.
	 *
	 * @param userId     사용자 ID
	 * @param missionIds 확인할 미션 ID
	 * @return 완료한 미션 ID. 없으면 빈 집합
	 */
	Set<UUID> findCompletedMissionIds(UUID userId, Collection<UUID> missionIds);
}
