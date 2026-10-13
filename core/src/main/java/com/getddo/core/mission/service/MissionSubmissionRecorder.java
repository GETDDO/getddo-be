package com.getddo.core.mission.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionAnswerInput;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionEvaluation;
import com.getddo.core.mission.domain.MissionRewardClaim;
import com.getddo.core.mission.domain.MissionRewardReceipt;
import com.getddo.core.mission.domain.MissionSubmission;
import com.getddo.core.mission.domain.MissionSubmissionCommand;
import com.getddo.core.mission.domain.MissionSubmissionResult;
import com.getddo.core.mission.domain.MissionType;
import com.getddo.core.mission.domain.QuizAnswer;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.domain.QuizQuestionProgress;
import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;
import com.getddo.core.mission.repository.MissionRepository;
import com.getddo.core.mission.repository.MissionRewardClaimRepository;
import com.getddo.core.mission.repository.MissionSubmissionRepository;
import com.getddo.core.mission.repository.QuizQuestionProgressRepository;
import com.getddo.core.ticket.domain.GrantCommand;
import com.getddo.core.ticket.domain.GrantResult;
import com.getddo.core.ticket.domain.GrantSource;
import com.getddo.core.ticket.domain.GrantSourceType;
import com.getddo.core.ticket.service.TicketGrantService;

/**
 * 미션 제출 한 건을 한 트랜잭션에서 처리한다. {@link MissionSubmissionService}만 호출한다.
 *
 * <p>제출·답변·정답 문항 진행·보상 청구·응모권 지급은 이 트랜잭션에서 함께 확정되거나 함께 취소된다. 트랜잭션은
 * {@code READ COMMITTED}다. 응모권 지급({@link TicketGrantService})이 청구 행을 잠그고 같은 트랜잭션에서 읽으므로, 격리 수준이 더
 * 높으면 시작 뒤에 저장된 행을 보지 못해 판단이 어긋날 수 있다.</p>
 *
 * <h2>처리 순서</h2>
 * <ol>
 *   <li>같은 제출 ID가 이미 저장됐으면 기존 결과를 돌려준다. 마감 뒤에도 같다(다른 사용자·미션·답변이면 충돌).</li>
 *   <li>미션을 읽고, 사용자가 이미 완료했으면 기존 완료 결과를 돌려준다(보상은 사용자·미션당 한 번).</li>
 *   <li>서버가 제출을 받은 시각이 운영 기간 안인지 확인하고 답변을 채점한다.</li>
 *   <li>제출·답변을 저장하고, 새로 맞힌 문항을 문항 ID 순서로 기록한다.</li>
 *   <li>완료했으면 보상 청구를 저장하고 그 청구로 응모권을 지급한다.</li>
 * </ol>
 *
 * <p>같은 사용자의 동시 완료는 DB의 유일 제약({@code (user_id, quiz_question_id)}, {@code (user_id, mission_id)}, 제출 PK)이
 * 직렬화한다. 늦은 쪽은 유일 제약 위반으로 실패하고, 재처리는 호출자가 새 트랜잭션에서 한다. 그러면 먼저 확정된 결과를 본다.
 * 정답 문항은 항상 같은 순서로 기록해 두 요청이 서로의 행을 기다리는 교착을 피한다.</p>
 */
@Service
@RequiredArgsConstructor
public class MissionSubmissionRecorder {

	private final MissionRepository missionRepository;
	private final MissionSubmissionRepository submissionRepository;
	private final QuizQuestionProgressRepository progressRepository;
	private final MissionRewardClaimRepository claimRepository;
	private final TicketGrantService grantService;
	private final TimeProvider timeProvider;

	/**
	 * 제출을 접수한다.
	 *
	 * @return 새로 접수한 결과({@code created=true}), 또는 같은 제출 ID나 이미 완료한 미션의 기존 결과({@code created=false})
	 * @throws MissionException 미션 없음, 운영 기간 밖, 답변 형식 오류, 같은 제출 ID의 다른 요청
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public MissionSubmissionResult record(MissionSubmissionCommand command) {
		Optional<MissionSubmission> existing = submissionRepository.findById(command.getSubmissionId());
		if (existing.isPresent()) {
			return replay(command, existing.get());
		}

		MissionDetail detail = missionRepository.findDetailById(command.getMissionId())
				.orElseThrow(() -> new MissionException(MissionErrorCode.MISSION_NOT_FOUND));
		Optional<MissionSubmission> completed = submissionRepository.findCompleted(command.getUserId(),
				command.getMissionId());
		if (completed.isPresent()) {
			return resultOf(completed.get(), false);
		}

		Mission mission = detail.getMission();
		// DATETIME(6)은 마이크로초 아래를 반올림해 저장하므로 미리 잘라 응답·재조회·저장 값이 어긋나지 않게 한다.
		Instant receivedAt = timeProvider.now().truncatedTo(ChronoUnit.MICROS);
		if (!mission.acceptsSubmissionAt(receivedAt)) {
			throw new MissionException(MissionErrorCode.MISSION_NOT_OPEN);
		}

		MissionEvaluation evaluation = detail.evaluate(command.getAnswers(),
				alreadyCorrectQuestions(command.getUserId(), detail));
		MissionSubmission saved = submissionRepository.insert(MissionSubmission.of(command.getSubmissionId(), receivedAt,
				mission, command.getUserId(), evaluation.isCompleted(), evaluation.getQuizAnswers(),
				evaluation.getSurveyAnswers()));
		recordCorrectQuestions(saved, evaluation.getNewlyCorrectQuestionIds());
		// 도착 시각이 기간 안이면 검증이 끝난 시각이 종료 뒤여도 달성·보상을 반영한다.
		MissionRewardReceipt reward = saved.isCompleted() ? claimAndGrant(saved, mission) : null;
		return new MissionSubmissionResult(saved.getId(), saved.getMissionId(), saved.getCreatedAt(), saved.isCompleted(),
				reward, true);
	}

	private Set<UUID> alreadyCorrectQuestions(UUID userId, MissionDetail detail) {
		if (detail.getMission().getMissionType() != MissionType.QUIZ) {
			return Set.of();
		}
		return progressRepository.findCorrectQuestionIds(userId,
				detail.getQuizQuestions().stream().map(QuizQuestion::getId).toList());
	}

	/** 이번 제출로 처음 맞힌 문항을 기록한다. 문항 ID 순서로 기록해 동시 요청과 같은 순서로 행을 잡는다. */
	private void recordCorrectQuestions(MissionSubmission saved, List<UUID> questionIds) {
		for (UUID questionId : questionIds) {
			QuizAnswer answer = saved.getQuizAnswers().stream()
					.filter(candidate -> candidate.getQuestionId().equals(questionId))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("정답으로 센 문항의 답변이 저장되지 않았다."));
			progressRepository.insert(QuizQuestionProgress.of(saved, answer.getId()));
		}
	}

	private MissionRewardReceipt claimAndGrant(MissionSubmission saved, Mission mission) {
		MissionRewardClaim claim = claimRepository.insert(MissionRewardClaim.of(saved));
		GrantResult granted = grantService.grant(new GrantCommand(saved.getUserId(), grantSource(claim),
				claim.getTicketCount(), mission.getTitle()));
		return receiptOf(granted);
	}

	/** 같은 제출 ID로 이미 저장된 제출의 기존 결과를 돌려준다. 다른 사용자·미션·답변이면 충돌이다. */
	private MissionSubmissionResult replay(MissionSubmissionCommand command, MissionSubmission stored) {
		if (!stored.getUserId().equals(command.getUserId()) || !stored.getMissionId().equals(command.getMissionId())
				|| !sameAnswers(command.getUserId(), command.getAnswers(), stored)) {
			throw new MissionException(MissionErrorCode.IDEMPOTENCY_CONFLICT);
		}
		return resultOf(stored, false);
	}

	/**
	 * 요청이 저장된 제출과 같은 본문인지 양방향으로 확인한다. 저장된 답변은 모두 요청의 같은 문항 답변과 같아야 하고, 요청에 저장되지
	 * 않은 문항의 답변이 더 있으면 다른 본문이다. 단, 퀴즈에서 이미 정답으로 완료한 문항에 다시 보낸 답변은 처음에도 저장하지
	 * 않았으므로, 그 문항이 사용자의 다른 제출로 정답 처리되어 있으면 같은 요청으로 본다. 설문은 문항이 정확히 같아야 한다.
	 */
	private boolean sameAnswers(UUID userId, List<MissionAnswerInput> inputs, MissionSubmission stored) {
		boolean storedAllInRequest = stored.getQuizAnswers().stream().allMatch(answer -> matches(inputs,
				answer.getQuestionId(), answer.getSelectedOptionId(), answer.getAnswerText()))
				&& stored.getSurveyAnswers().stream().allMatch(answer -> matches(inputs, answer.getQuestionId(),
						answer.getSelectedOptionId(), answer.getAnswerText()));
		if (!storedAllInRequest) {
			return false;
		}
		Set<UUID> storedQuestions = new HashSet<>();
		stored.getQuizAnswers().forEach(answer -> storedQuestions.add(answer.getQuestionId()));
		stored.getSurveyAnswers().forEach(answer -> storedQuestions.add(answer.getQuestionId()));
		List<UUID> extra = inputs.stream().map(MissionAnswerInput::getQuestionId)
				.filter(questionId -> !storedQuestions.contains(questionId))
				.toList();
		if (extra.isEmpty()) {
			return true;
		}
		boolean quiz = !stored.getQuizAnswers().isEmpty();
		return quiz && progressRepository.findCorrectQuestionIds(userId, extra).containsAll(extra);
	}

	private static boolean matches(List<MissionAnswerInput> inputs, UUID questionId, UUID optionId, String text) {
		return inputs.stream().anyMatch(input -> input.getQuestionId().equals(questionId)
				&& Objects.equals(input.getSelectedOptionId(), optionId)
				&& Objects.equals(blankToNull(input.getAnswerText()), text));
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text;
	}

	/** 저장된 제출의 결과를 만든다. 완료한 제출이면 그 보상 청구로 확정된 응모권 지급 결과를 다시 읽는다. */
	private MissionSubmissionResult resultOf(MissionSubmission submission, boolean created) {
		MissionRewardReceipt reward = null;
		if (submission.isCompleted()) {
			MissionRewardClaim claim = claimRepository
					.findByUserAndMission(submission.getUserId(), submission.getMissionId())
					.filter(found -> found.getMissionSubmissionId().equals(submission.getId()))
					.orElseThrow(() -> new IllegalStateException("완료한 미션 제출에 보상 청구가 없다."));
			reward = receiptOf(grantService.findGrant(grantSource(claim))
					.orElseThrow(() -> new IllegalStateException("미션 보상 청구에 응모권 지급 결과가 없다.")));
		}
		return new MissionSubmissionResult(submission.getId(), submission.getMissionId(), submission.getCreatedAt(),
				submission.isCompleted(), reward, created);
	}

	private static GrantSource grantSource(MissionRewardClaim claim) {
		return new GrantSource(GrantSourceType.MISSION, claim.getId());
	}

	private static MissionRewardReceipt receiptOf(GrantResult granted) {
		return new MissionRewardReceipt(granted.getQuantity(), granted.getGrade(), granted.getGrantedAt(),
				granted.getExpiresAt());
	}
}
