package com.getddo.core.mission.domain;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 같은 제출 ID의 재전송이 원래 요청과 같은 본문인지 판정하는 요청 해시를 검증한다. */
class MissionSubmissionCommandTest {

	private final UUID userId = UUID.randomUUID();
	private final UUID missionId = UUID.randomUUID();
	private final UUID submissionId = UUID.randomUUID();
	private final UUID q1 = UUID.randomUUID();
	private final UUID q2 = UUID.randomUUID();
	private final UUID option = UUID.randomUUID();

	private MissionSubmissionCommand command(MissionAnswerInput... answers) {
		return new MissionSubmissionCommand(userId, missionId, submissionId, List.of(answers));
	}

	@Test
	@DisplayName("요청 해시는 64자리 소문자 16진수이고 같은 요청이면 같으며 답변 순서와 제출 ID에는 영향을 받지 않는다")
	void hashIsStableAndIgnoresAnswerOrderAndSubmissionId() {
		// given
		MissionAnswerInput first = new MissionAnswerInput(q1, option, null);
		MissionAnswerInput second = new MissionAnswerInput(q2, null, "겟또");
		// when
		String hash = command(first, second).getRequestHash();
		// then
		assertThat(hash).matches("[0-9a-f]{64}");
		assertThat(command(first, second).getRequestHash()).isEqualTo(hash);
		assertThat(command(second, first).getRequestHash()).isEqualTo(hash);
		assertThat(new MissionSubmissionCommand(userId, missionId, UUID.randomUUID(), List.of(first, second))
				.getRequestHash()).isEqualTo(hash);
	}

	@Test
	@DisplayName("사용자·미션·문항·선택지·입력 문자열·답변 개수 중 하나라도 다르면 해시가 다르다")
	void hashChangesWithAnyDifferenceInTheRequest() {
		// given
		String base = command(new MissionAnswerInput(q1, option, null)).getRequestHash();
		// when
		// then
		assertThat(new MissionSubmissionCommand(UUID.randomUUID(), missionId, submissionId,
				List.of(new MissionAnswerInput(q1, option, null))).getRequestHash()).isNotEqualTo(base);
		assertThat(new MissionSubmissionCommand(userId, UUID.randomUUID(), submissionId,
				List.of(new MissionAnswerInput(q1, option, null))).getRequestHash()).isNotEqualTo(base);
		assertThat(command(new MissionAnswerInput(q2, option, null)).getRequestHash()).isNotEqualTo(base);
		assertThat(command(new MissionAnswerInput(q1, UUID.randomUUID(), null)).getRequestHash()).isNotEqualTo(base);
		assertThat(command(new MissionAnswerInput(q1, null, "겟또")).getRequestHash()).isNotEqualTo(base);
		assertThat(command(new MissionAnswerInput(q1, option, null), new MissionAnswerInput(q2, null, "겟또"))
				.getRequestHash()).isNotEqualTo(base);
	}

	@Test
	@DisplayName("공백뿐인 입력 문자열은 보내지 않은 것과 같고 입력 문자열의 내용은 구분한다")
	void hashTreatsBlankTextAsAbsentAndDistinguishesContent() {
		// given
		String absent = command(new MissionAnswerInput(q1, option, null)).getRequestHash();
		// when
		// then
		assertThat(command(new MissionAnswerInput(q1, option, "  ")).getRequestHash()).isEqualTo(absent);
		assertThat(command(new MissionAnswerInput(q1, null, "겟또")).getRequestHash())
				.isNotEqualTo(command(new MissionAnswerInput(q1, null, "겟또 ")).getRequestHash());
		assertThat(command(new MissionAnswerInput(q1, null, "Get Ddo")).getRequestHash())
				.isNotEqualTo(command(new MissionAnswerInput(q1, null, "get ddo")).getRequestHash());
	}

	@Test
	@DisplayName("구분 기호가 들어간 입력 문자열이 다른 답변 구성과 같은 해시를 만들지 못한다")
	void hashIsNotAmbiguousAcrossAnswerBoundaries() {
		// given
		MissionAnswerInput crafted = new MissionAnswerInput(q1, null, "a|q=" + q2 + ",o=-,t=1:b");
		// when
		// then
		assertThat(command(crafted).getRequestHash()).isNotEqualTo(command(new MissionAnswerInput(q1, null, "a"),
				new MissionAnswerInput(q2, null, "b")).getRequestHash());
	}

	@Test
	@DisplayName("답변 목록이 없으면 해시도 없다")
	void hasNoHashWithoutAnswers() {
		// given
		// when
		// then
		assertThat(new MissionSubmissionCommand(userId, missionId, submissionId, null).getRequestHash()).isNull();
	}
}
