package com.getddo.db.mission;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.mission.domain.Mission;
import com.getddo.core.mission.domain.MissionDetail;
import com.getddo.core.mission.domain.MissionType;
import com.getddo.core.mission.domain.QuizOption;
import com.getddo.core.mission.domain.QuizQuestion;
import com.getddo.core.mission.domain.QuizQuestionType;
import com.getddo.core.mission.domain.SurveyOption;
import com.getddo.core.mission.domain.SurveyQuestion;
import com.getddo.core.mission.domain.SurveyQuestionType;
import com.getddo.core.mission.repository.MissionRepository;
import com.getddo.db.ticket.MutableClock;
import com.getddo.db.ticket.TicketGrantSeeds;
import com.getddo.db.ticket.TicketIntegrationTestApplication;

/**
 * 미션 통합 테스트 공통 기반. 응모권 통합 테스트와 같은 컨텍스트(같은 MySQL)를 쓴다.
 *
 * <p>테스트 메서드를 트랜잭션으로 감싸지 않는다. 각 테스트는 자기 사용자·관리자·보상 정책을 새로 만들고 끝나면 지운다.</p>
 */
@SpringBootTest(
		classes = TicketIntegrationTestApplication.class,
		properties = "spring.jpa.hibernate.ddl-auto=validate")
public abstract class MissionIntegrationTestSupport {

	protected static final Instant OPENS = Instant.parse("2026-09-01T00:00:00Z");
	protected static final Instant CLOSES = Instant.parse("2026-12-01T00:00:00Z");

	@Autowired
	protected TransactionTemplate transaction;
	@Autowired
	protected JdbcTemplate jdbc;
	@Autowired
	protected MutableClock clock;
	@Autowired
	protected MissionRepository missionRepository;

	protected TicketGrantSeeds seeds;
	protected UUID userId;
	/** 미션 등록자로 쓰는 사용자. */
	protected UUID adminId;
	protected UUID rewardPolicyId;

	@BeforeEach
	void setUpMissionTest() {
		clock.set(TicketIntegrationTestApplication.INITIAL_TIME);
		seeds = new TicketGrantSeeds(jdbc);
		userId = seeds.user();
		adminId = seeds.user();
		rewardPolicyId = seeds.missionRewardPolicy(adminId);
	}

	@AfterEach
	void cleanUpMissionTest() {
		seeds.cleanUp();
	}

	protected long count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Long.class, args);
	}

	protected Mission newMission(String title, MissionType type) {
		return Mission.register(rewardPolicyId, adminId, title, "설명", type, OPENS, CLOSES, null);
	}

	/** 단일 선택·단답·OX 퀴즈 문항을 한 개씩 가진 미션을 저장한다. */
	protected MissionDetail saveQuizMission(String title) {
		MissionDetail detail = new MissionDetail(newMission(title, MissionType.QUIZ), List.of(
				QuizQuestion.of(QuizQuestionType.SINGLE_CHOICE, "수도는?", 1, null,
						List.of(QuizOption.of("서울", 1, true), QuizOption.of("부산", 2, false))),
				QuizQuestion.of(QuizQuestionType.SHORT_ANSWER, "브랜드 이름은?", 2, "겟또", List.of()),
				QuizQuestion.of(QuizQuestionType.OX, "참인가?", 3, null,
						List.of(QuizOption.of("O", 1, true), QuizOption.of("X", 2, false)))), List.of());
		return transaction.execute(status -> missionRepository.insert(detail));
	}

	/** 필수 단일 선택과 선택 자유 입력 설문 문항을 가진 미션을 저장한다. */
	protected MissionDetail saveSurveyMission(String title) {
		MissionDetail detail = new MissionDetail(newMission(title, MissionType.SURVEY), List.of(), List.of(
				SurveyQuestion.of(SurveyQuestionType.SINGLE_CHOICE, "만족도는?", true, 1,
						List.of(SurveyOption.of("좋음", 1), SurveyOption.of("보통", 2))),
				SurveyQuestion.of(SurveyQuestionType.FREE_TEXT, "의견은?", false, 2, List.of())));
		return transaction.execute(status -> missionRepository.insert(detail));
	}
}
