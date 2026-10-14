package com.getddo.core.mission.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.getddo.core.mission.domain.MissionRewardClaim;
import com.getddo.core.mission.domain.MissionRewardReceipt;
import com.getddo.core.mission.domain.MissionSubmission;
import com.getddo.core.mission.domain.MissionSubmissionResult;
import com.getddo.core.mission.repository.MissionRewardClaimRepository;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.service.TicketGrantService;

/**
 * 저장된 미션 제출을 결과로 옮긴다. 완료한 제출이면 그 제출의 보상 청구로 확정된 응모권 지급 결과를 다시 읽어 보상으로
 * 담는다. 제출 접수({@link MissionSubmissionRecorder})와 내 제출 조회({@link MissionSubmissionQueryService})가 같은
 * 규칙을 쓰도록 한곳에 둔다.
 *
 * <p>트랜잭션을 만들지 않는다. 호출하는 서비스의 트랜잭션 안에서 읽는다.</p>
 */
@Component
@RequiredArgsConstructor
public class MissionSubmissionResultReader {

	private final MissionRewardClaimRepository claimRepository;
	private final TicketGrantService grantService;

	/**
	 * 저장된 제출의 결과를 만든다.
	 *
	 * @param submission 저장된 제출
	 * @param created    이번 요청으로 새로 접수한 제출인지 여부
	 * @return 완료한 제출이면 보상을 포함한 결과
	 * @throws IllegalStateException 완료한 제출에 보상 청구나 응모권 지급 결과가 없는 경우(저장된 데이터가 어긋난 것이다)
	 */
	public MissionSubmissionResult read(MissionSubmission submission, boolean created) {
		MissionRewardReceipt reward = null;
		if (submission.isCompleted()) {
			MissionRewardClaim claim = claimRepository
					.findByUserAndMission(submission.getUserId(), submission.getMissionId())
					.filter(found -> found.getMissionSubmissionId().equals(submission.getId()))
					.orElseThrow(() -> new IllegalStateException("완료한 미션 제출에 보상 청구가 없다. submissionId=" + submission.getId()
							+ ", userId=" + submission.getUserId() + ", missionId=" + submission.getMissionId()));
			reward = receiptOf(grantService.findGrant(grantSource(claim))
					.orElseThrow(() -> new IllegalStateException("미션 보상 청구에 응모권 지급 결과가 없다. claimId=" + claim.getId())));
		}
		return new MissionSubmissionResult(submission.getId(), submission.getMissionId(), submission.getCreatedAt(),
				submission.isCompleted(), reward, created);
	}

	/** 보상 청구로 응모권 지급을 찾거나 요청할 때 쓰는 출처다. */
	static GrantSource grantSource(MissionRewardClaim claim) {
		return new GrantSource(GrantSourceType.MISSION, claim.getId());
	}

	/** 응모권 서비스가 확정한 지급 결과를 응답용 보상으로 옮긴다. */
	static MissionRewardReceipt receiptOf(GrantResult granted) {
		return new MissionRewardReceipt(granted.getQuantity(), granted.getGrade(), granted.getGrantedAt(),
				granted.getExpiresAt());
	}
}
