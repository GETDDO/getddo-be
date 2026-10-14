package com.getddo.db.mission;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import com.getddo.core.mission.domain.MissionAnswerInput;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionSubmissionCommand;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.service.MissionSubmissionService;
import com.getddo.core.ticket.service.TicketGrantService;

import static com.getddo.db.ticket.TicketGrantSeeds.bytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * 응모권 지급이 실패하면 같은 트랜잭션의 제출·답변·정답 진행·보상 청구가 모두 취소되는지 검증한다.
 *
 * <p>지급 서비스만 예외를 던지게 대체해 실제 MySQL에서 롤백을 확인한다. 대체한 Bean 때문에 다른 통합 테스트와 컨텍스트가
 * 달라 별도 클래스로 둔다.</p>
 */
class MissionSubmissionRollbackIntegrationTest extends MissionIntegrationTestSupport {

	@Autowired
	private MissionSubmissionService submissionService;
	@MockitoSpyBean
	private TicketGrantService grantService;

	@Test
	@DisplayName("응모권 지급이 실패하면 제출·답변·정답 진행·보상 청구가 모두 취소된다")
	void rollsBackEverythingWhenGrantFails() {
		// given
		MissionDetail quiz = saveQuizMission("퀴즈 미션");
		List<QuizQuestion> q = quiz.getQuizQuestions();
		MissionSubmissionCommand command = new MissionSubmissionCommand(userId, quiz.getMission().getId(),
				UUID.randomUUID(), List.of(
						new MissionAnswerInput(q.get(0).getId(), q.get(0).getOptions().get(0).getId(), null),
						new MissionAnswerInput(q.get(1).getId(), null, "겟또"),
						new MissionAnswerInput(q.get(2).getId(), q.get(2).getOptions().get(0).getId(), null)));
		// 스파이는 트랜잭션 프록시 안쪽에 있다. 프록시를 거쳐 스터빙하면 호출 자체가 MANDATORY 검사에 걸리므로 대상 객체에 직접 스터빙한다.
		doThrow(new IllegalStateException("grant failure")).when((TicketGrantService) AopTestUtils.getUltimateTargetObject(grantService))
				.grant(any());
		// when
		// then
		assertThatThrownBy(() -> submissionService.submit(command))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("grant failure");
		assertThat(count("select count(*) from mission_submissions where user_id = ?", bytes(userId))).isZero();
		assertThat(count("""
				select count(*) from quiz_answers a join mission_submissions s on s.id = a.submission_id
				where s.user_id = ?
				""", bytes(userId))).isZero();
		assertThat(count("select count(*) from quiz_question_progress where user_id = ?", bytes(userId))).isZero();
		assertThat(count("select count(*) from mission_reward_claims where user_id = ?", bytes(userId))).isZero();
		assertThat(count("select count(*) from tickets where user_id = ?", bytes(userId))).isZero();
	}
}
