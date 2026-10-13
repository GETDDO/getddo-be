package com.getddo.core.mission.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.common.pagination.PageQuery;
import com.getddo.core.common.pagination.PageResult;
import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionRewardClaim;
import com.getddo.core.mission.domain.MissionStatus;
import com.getddo.core.mission.domain.MissionSubmission;
import com.getddo.core.mission.domain.MissionSubmissionResult;
import com.getddo.core.mission.domain.MissionType;
import com.getddo.core.mission.domain.QuizAnswer;
import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;
import com.getddo.core.mission.repository.MissionRepository;
import com.getddo.core.mission.repository.MissionRewardClaimRepository;
import com.getddo.core.mission.repository.MissionSubmissionRepository;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.TicketGrade;
import com.getddo.core.ticket.service.TicketGrantService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 내 제출 조회가 오답은 보상 없이, 완료한 제출은 확정된 응모권 보상과 함께 돌려주는지 검증한다. */
class MissionSubmissionQueryServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();
	private static final UUID MISSION_ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-10-13T03:00:00Z");
	private static final Instant EXPIRES_AT = Instant.parse("2026-10-31T15:00:00Z");

	private final MissionRepository missionRepository = mock(MissionRepository.class);
	private final MissionSubmissionRepository submissionRepository = mock(MissionSubmissionRepository.class);
	private final MissionRewardClaimRepository claimRepository = mock(MissionRewardClaimRepository.class);
	private final TicketGrantService grantService = mock(TicketGrantService.class);
	private final MissionSubmissionQueryService service = new MissionSubmissionQueryService(missionRepository,
			submissionRepository, new MissionSubmissionResultReader(claimRepository, grantService));

	private static Mission mission() {
		return new Mission(MISSION_ID, UUID.randomUUID(), UUID.randomUUID(), "미션", "설명", MissionType.QUIZ, NOW,
				NOW.plusSeconds(86_400), MissionStatus.ACTIVE, null, NOW, NOW);
	}

	private static MissionSubmission submission(UUID id, boolean completed) {
		QuizAnswer answer = new QuizAnswer(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, completed);
		return new MissionSubmission(id, MISSION_ID, UUID.randomUUID(), USER_ID, completed, NOW, "0".repeat(64),
				List.of(answer), List.of());
	}

	private static MissionRewardClaim claimFor(MissionSubmission submission) {
		return new MissionRewardClaim(UUID.randomUUID(), submission.getRewardPolicyId(), MISSION_ID, submission.getId(),
				USER_ID, MissionRewardClaim.sourceKey(MISSION_ID), MissionRewardClaim.REWARD_TICKET_COUNT, NOW);
	}

	@Test
	@DisplayName("오답 제출은 보상 없이, 완료한 제출은 그 보상 청구의 응모권 지급 결과와 함께 최신순 그대로 돌려주고 페이지 정보를 보존한다")
	void returnsIncompleteWithoutRewardAndCompletedWithConfirmedReward() {
		// given
		MissionSubmission wrong = submission(UUID.randomUUID(), false);
		MissionSubmission done = submission(UUID.randomUUID(), true);
		MissionRewardClaim claim = claimFor(done);
		when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission()));
		when(submissionRepository.findByUser(USER_ID, MISSION_ID, new PageQuery(1, 20)))
				.thenReturn(new PageResult<>(List.of(done, wrong), 1, 20, 2));
		when(claimRepository.findByUserAndMission(USER_ID, MISSION_ID)).thenReturn(Optional.of(claim));
		when(grantService.findGrant(any())).thenReturn(
				Optional.of(new GrantResult(1, TicketGrade.SILVER, NOW, EXPIRES_AT, true)));
		// when
		PageResult<MissionSubmissionResult> result = service.findMySubmissions(USER_ID, MISSION_ID, new PageQuery(1, 20));
		// then
		assertThat(result.getItems()).extracting(MissionSubmissionResult::getSubmissionId)
				.containsExactly(done.getId(), wrong.getId());
		MissionSubmissionResult completed = result.getItems().get(0);
		assertThat(completed.isCompleted()).isTrue();
		assertThat(completed.isCreated()).isFalse();
		assertThat(completed.getReceivedAt()).isEqualTo(NOW);
		assertThat(completed.getReward().getTicketCount()).isEqualTo(1);
		assertThat(completed.getReward().getGrade()).isEqualTo(TicketGrade.SILVER);
		assertThat(completed.getReward().getExpiresAt()).isEqualTo(EXPIRES_AT);
		MissionSubmissionResult incomplete = result.getItems().get(1);
		assertThat(incomplete.isCompleted()).isFalse();
		assertThat(incomplete.getReward()).isNull();
		assertThat(result.getPage()).isEqualTo(1);
		assertThat(result.getSize()).isEqualTo(20);
		assertThat(result.getTotalElements()).isEqualTo(2);
	}

	@Test
	@DisplayName("오답만 있으면 보상 청구나 응모권 지급을 조회하지 않는다")
	void doesNotLookUpRewardForIncompleteSubmissions() {
		// given
		when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission()));
		when(submissionRepository.findByUser(any(), any(), any())).thenReturn(
				new PageResult<>(List.of(submission(UUID.randomUUID(), false)), 1, 20, 1));
		// when
		service.findMySubmissions(USER_ID, MISSION_ID, new PageQuery(1, 20));
		// then
		verify(claimRepository, never()).findByUserAndMission(any(), any());
		verify(grantService, never()).findGrant(any());
	}

	@Test
	@DisplayName("제출이 없으면 빈 페이지를 돌려준다")
	void returnsEmptyPageWithoutSubmissions() {
		// given
		when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission()));
		when(submissionRepository.findByUser(any(), any(), any())).thenReturn(new PageResult<>(List.of(), 1, 20, 0));
		// when
		PageResult<MissionSubmissionResult> result = service.findMySubmissions(USER_ID, MISSION_ID, new PageQuery(1, 20));
		// then
		assertThat(result.getItems()).isEmpty();
		assertThat(result.getTotalElements()).isZero();
	}

	@Test
	@DisplayName("없는 미션은 MISSION-003이고 제출을 조회하지 않는다")
	void throwsWhenMissionIsMissing() {
		// given
		when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.empty());
		// when
		// then
		assertThatThrownBy(() -> service.findMySubmissions(USER_ID, MISSION_ID, new PageQuery(1, 20)))
				.isInstanceOfSatisfying(MissionException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(MissionErrorCode.MISSION_NOT_FOUND));
		verify(submissionRepository, never()).findByUser(any(), any(), any());
	}

	@Test
	@DisplayName("완료한 제출에 보상 청구가 없으면 저장된 데이터가 어긋난 것이므로 숨기지 않고 실패한다")
	void failsWhenCompletedSubmissionHasNoClaim() {
		// given
		when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission()));
		when(submissionRepository.findByUser(any(), any(), any())).thenReturn(
				new PageResult<>(List.of(submission(UUID.randomUUID(), true)), 1, 20, 1));
		when(claimRepository.findByUserAndMission(USER_ID, MISSION_ID)).thenReturn(Optional.empty());
		// when
		// then
		assertThatThrownBy(() -> service.findMySubmissions(USER_ID, MISSION_ID, new PageQuery(1, 20)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("완료한 제출이 둘인데 보상 청구가 하나뿐이면 청구와 이어지지 않은 제출에서 숨기지 않고 실패한다")
	void failsWhenSecondCompletedSubmissionHasNoMatchingClaim() {
		// given
		MissionSubmission first = submission(UUID.randomUUID(), true);
		MissionSubmission second = submission(UUID.randomUUID(), true);
		when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission()));
		when(submissionRepository.findByUser(any(), any(), any())).thenReturn(
				new PageResult<>(List.of(first, second), 1, 20, 2));
		when(claimRepository.findByUserAndMission(USER_ID, MISSION_ID)).thenReturn(Optional.of(claimFor(first)));
		when(grantService.findGrant(any())).thenReturn(
				Optional.of(new GrantResult(1, TicketGrade.SILVER, NOW, EXPIRES_AT, true)));
		// when
		// then
		assertThatThrownBy(() -> service.findMySubmissions(USER_ID, MISSION_ID, new PageQuery(1, 20)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(second.getId().toString());
	}

	@Test
	@DisplayName("보상 청구는 있는데 응모권 지급 결과가 없으면 보상 없이 완료로 보이게 하지 않고 실패한다")
	void failsWhenClaimHasNoConfirmedGrant() {
		// given
		MissionSubmission done = submission(UUID.randomUUID(), true);
		MissionRewardClaim claim = claimFor(done);
		when(missionRepository.findById(MISSION_ID)).thenReturn(Optional.of(mission()));
		when(submissionRepository.findByUser(any(), any(), any())).thenReturn(
				new PageResult<>(List.of(done), 1, 20, 1));
		when(claimRepository.findByUserAndMission(USER_ID, MISSION_ID)).thenReturn(Optional.of(claim));
		when(grantService.findGrant(any())).thenReturn(Optional.empty());
		// when
		// then
		assertThatThrownBy(() -> service.findMySubmissions(USER_ID, MISSION_ID, new PageQuery(1, 20)))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining(claim.getId().toString());
	}
}
