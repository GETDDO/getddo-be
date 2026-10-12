package com.getddo.core.drawing.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/** 공개 모델의 불변 조건을 검증한다. 취소·재추첨의 실제 연동 검증은 아니다. */
class DrawPublicationTest {
	private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");
	private final UUID eventId = UUID.randomUUID(), initialRun = UUID.randomUUID(), replacementRun = UUID.randomUUID();

	@Test
	@DisplayName("유지 결과와 새 결과의 원본 실행·확인 참조를 구분해 보존하고 외부 변경을 차단한다")
	void preservesMixedSourcesAndConfirmationReferences() {
		// given: 업무 명단을 구성하는 연동 검증과 구분하여 값 보존·복사만 검증한다.
		DrawPublication.Entry retained = new DrawPublication.Entry(UUID.randomUUID(), initialRun, null);
		DrawPublication.Entry replaced = new DrawPublication.Entry(UUID.randomUUID(), replacementRun, UUID.randomUUID());
		List<DrawPublication.Entry> mutable = new ArrayList<>(List.of(retained, replaced));
		// when
		DrawPublication publication = new DrawPublication(UUID.randomUUID(), eventId, replacementRun, 1,
				DrawPublicationType.INITIAL, "취소 후 최초 공개", UUID.randomUUID(), NOW, NOW, mutable);
		mutable.clear();
		// then
		assertThat(publication.entries()).containsExactly(retained, replaced);
		assertThatThrownBy(() -> publication.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("관리자 확인 또는 사유가 없는 갱신 버전을 거절한다")
	void rejectsUnconfirmedUpdateMetadata() {
		// when / then
		assertThatThrownBy(() -> new DrawPublication(UUID.randomUUID(), eventId, replacementRun, 2,
				DrawPublicationType.UPDATE, "사유", null, null, NOW, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DrawPublication(UUID.randomUUID(), eventId, replacementRun, 2,
				DrawPublicationType.UPDATE, " ", UUID.randomUUID(), NOW, NOW, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("공개 이후 확인 시각과 동일 결과의 중복 등록을 거절한다")
	void rejectsFutureConfirmationAndDuplicateResult() {
		// given
		DrawPublication.Entry entry = new DrawPublication.Entry(UUID.randomUUID(), initialRun, null);
		// when / then
		assertThatThrownBy(() -> new DrawPublication(UUID.randomUUID(), eventId, replacementRun, 2,
				DrawPublicationType.UPDATE, "사유", UUID.randomUUID(), NOW.plusSeconds(1), NOW, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DrawPublication(UUID.randomUUID(), eventId, initialRun, 1,
				DrawPublicationType.INITIAL, null, null, null, NOW, List.of(entry, entry)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("공개 읽기 모델은 필수 식별자·자리 번호·확인의 실행 관계를 생성 시 검증한다")
	void validatesReadModelIdentityAndRelations() {
		// given
		DrawResultConfirmation confirmation = new DrawResultConfirmation(UUID.randomUUID(), replacementRun,
				UUID.randomUUID(), NOW);
		// when / then
		assertThatThrownBy(() -> new DrawPublicationRun(null, eventId, 1, DrawRunStatus.CONFIRMED,
				NOW, List.of(), null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublicationRun(initialRun, null, 1, DrawRunStatus.CONFIRMED,
				NOW, List.of(), null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublicationRun(initialRun, eventId, 1, null,
				NOW, List.of(), null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublicationRun(initialRun, eventId, 0, DrawRunStatus.CONFIRMED,
				NOW, List.of(), null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DrawPublicationRun(initialRun, eventId, 1, DrawRunStatus.CONFIRMED,
				NOW, List.of(), confirmation)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DrawPublicationCancellation(null, UUID.randomUUID(), null, "사유"))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublicationCancellation(UUID.randomUUID(), null, null, "사유"))
				.isInstanceOf(NullPointerException.class);
		assertThat(new DrawPublicationCancellation(UUID.randomUUID(), UUID.randomUUID(), null, "사유").replacementRunId()).isNull();
		assertThatThrownBy(() -> new DrawPublicationChange(null, List.of())).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublicationChange.SlotChange(UUID.randomUUID(), 0, null, null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DrawPublicationChange.SlotChange(null, 1, null, null, null, null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublicationComposition(false, null, null, null, List.of()))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublicationConfirmation(eventId, null, null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublishedSlot(UUID.randomUUID(), 0, UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DrawPublishedSlot(UUID.randomUUID(), 1, UUID.randomUUID(), null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublishedSlot(null, 1, UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new DrawPublishedSlot(UUID.randomUUID(), 1, null, UUID.randomUUID()))
				.isInstanceOf(NullPointerException.class);
	}
}
