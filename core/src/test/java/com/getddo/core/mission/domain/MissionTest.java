package com.getddo.core.mission.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.mission.exception.MissionErrorCode;
import com.getddo.core.mission.exception.MissionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MissionTest {

	private static final Instant STARTS = Instant.parse("2026-10-01T00:00:00Z");
	private static final Instant ENDS = Instant.parse("2026-10-31T00:00:00Z");

	private static Mission register(String title, String description, Instant startsAt, Instant endsAt,
			String imageKey) {
		return Mission.register(UUID.randomUUID(), UUID.randomUUID(), title, description, MissionType.SURVEY,
				startsAt, endsAt, imageKey);
	}

	@Test
	@DisplayName("새 미션은 운영 중 상태로 등록되고 저장 전이라 ID와 시각이 없다")
	void registersActiveMission() {
		// given
		UUID policyId = UUID.randomUUID();
		UUID adminId = UUID.randomUUID();
		// when
		Mission mission = Mission.register(policyId, adminId, "가을 설문", "설명", MissionType.QUIZ, STARTS, ENDS, null);
		// then
		assertThat(mission.getStatus()).isEqualTo(MissionStatus.ACTIVE);
		assertThat(mission.getRewardPolicyId()).isEqualTo(policyId);
		assertThat(mission.getCreatedBy()).isEqualTo(adminId);
		assertThat(mission.getMissionType()).isEqualTo(MissionType.QUIZ);
		assertThat(mission.getId()).isNull();
		assertThat(mission.getCreatedAt()).isNull();
		assertThat(mission.getImageKey()).isNull();
	}

	@Test
	@DisplayName("제목은 비어 있거나 200자를 넘으면 안 되고 200자는 허용한다")
	void titleBoundary() {
		// given
		// when
		// then
		assertThat(register("가".repeat(200), "설명", STARTS, ENDS, null).getTitle()).hasSize(200);
		assertInvalid(() -> register("가".repeat(201), "설명", STARTS, ENDS, null));
		assertInvalid(() -> register(" ", "설명", STARTS, ENDS, null));
	}

	@Test
	@DisplayName("설명이 비어 있으면 등록할 수 없다")
	void descriptionRequired() {
		// given
		// when
		// then
		assertInvalid(() -> register("제목", " ", STARTS, ENDS, null));
	}

	@Test
	@DisplayName("종료 시각은 시작 시각보다 늦어야 한다")
	void endMustBeAfterStart() {
		// given
		// when
		// then
		assertInvalid(() -> register("제목", "설명", STARTS, STARTS, null));
		assertInvalid(() -> register("제목", "설명", ENDS, STARTS, null));
		assertThat(register("제목", "설명", STARTS, STARTS.plusNanos(1000), null)).isNotNull();
	}

	@Test
	@DisplayName("이미지 경로는 500자까지 허용한다")
	void imageKeyBoundary() {
		// given
		// when
		// then
		assertThat(register("제목", "설명", STARTS, ENDS, "a".repeat(500)).getImageKey()).hasSize(500);
		assertInvalid(() -> register("제목", "설명", STARTS, ENDS, "a".repeat(501)));
	}

	@Test
	@DisplayName("저장소가 복원하는 생성자도 종료가 시작보다 늦지 않거나 제목·이미지 경로가 길이를 넘으면 거절한다")
	void restoringConstructorKeepsDatabaseInvariants() {
		// given
		UUID id = UUID.randomUUID();
		// when
		// then
		assertInvalid(() -> new Mission(id, id, id, "제목", "설명", MissionType.SURVEY, ENDS, STARTS, MissionStatus.ACTIVE,
				null, STARTS, STARTS));
		assertInvalid(() -> new Mission(id, id, id, "제목", "설명", MissionType.SURVEY, STARTS, STARTS, MissionStatus.ACTIVE,
				null, STARTS, STARTS));
		assertInvalid(() -> new Mission(id, id, id, "가".repeat(201), "설명", MissionType.SURVEY, STARTS, ENDS,
				MissionStatus.ACTIVE, null, STARTS, STARTS));
		assertInvalid(() -> new Mission(id, id, id, "제목", "설명", MissionType.SURVEY, STARTS, ENDS, MissionStatus.ACTIVE,
				"a".repeat(501), STARTS, STARTS));
	}

	@Test
	@DisplayName("제출은 시작 시각 정각부터 인정하고 종료 시각 정각부터는 인정하지 않는다")
	void submissionWindowIsHalfOpen() {
		// given
		Mission mission = register("제목", "설명", STARTS, ENDS, null);
		// when
		// then
		assertThat(mission.acceptsSubmissionAt(STARTS.minusNanos(1000))).isFalse();
		assertThat(mission.acceptsSubmissionAt(STARTS)).isTrue();
		assertThat(mission.acceptsSubmissionAt(ENDS.minusNanos(1000))).isTrue();
		assertThat(mission.acceptsSubmissionAt(ENDS)).isFalse();
	}

	@Test
	@DisplayName("기간 판정은 운영 상태와 무관하게 도착 시각만 보므로 종료 상태가 된 뒤에도 기간 안에 도착한 제출을 기간 안으로 판정한다")
	void periodCheckIgnoresStatus() {
		// given
		Mission active = register("제목", "설명", STARTS, ENDS, null);
		Mission ended = new Mission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "제목", "설명",
				MissionType.SURVEY, STARTS, ENDS, MissionStatus.ENDED, null, STARTS, STARTS);
		Instant arrivedInPeriod = ENDS.minusSeconds(1);
		// when
		// then
		assertThat(active.isWithinPeriod(STARTS.minusNanos(1000))).isFalse();
		assertThat(active.isWithinPeriod(STARTS)).isTrue();
		assertThat(active.isWithinPeriod(ENDS.minusNanos(1000))).isTrue();
		assertThat(active.isWithinPeriod(ENDS)).isFalse();
		assertThat(ended.isWithinPeriod(arrivedInPeriod)).isTrue();
		assertThat(ended.acceptsSubmissionAt(arrivedInPeriod)).isFalse();
	}

	@Test
	@DisplayName("종료된 미션은 기간 안이어도 제출을 인정하지 않는다")
	void endedMissionRejectsSubmission() {
		// given
		Mission ended = new Mission(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "제목", "설명",
				MissionType.SURVEY, STARTS, ENDS, MissionStatus.ENDED, null, STARTS, STARTS);
		// when
		// then
		assertThat(ended.acceptsSubmissionAt(STARTS.plusSeconds(1))).isFalse();
	}

	private static void assertInvalid(Runnable action) {
		assertThatThrownBy(action::run)
				.isInstanceOfSatisfying(MissionException.class,
						e -> assertThat(e.getErrorCode()).isEqualTo(MissionErrorCode.INVALID_MISSION_DEFINITION));
	}
}
