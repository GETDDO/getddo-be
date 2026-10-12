package com.getddo.db.drawing.mapper;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DrawInspectionRulesMapperTest {
	private final DrawInspectionRulesMapper mapper = new DrawInspectionRulesMapper();
	private final UUID prizeId = UUID.randomUUID(), originalId = UUID.randomUUID(), resultId = UUID.randomUUID(), userId = UUID.randomUUID();

	@Test
	@DisplayName("기존 버전 1 JSON은 공용 입력 변경 없이 복원한다")
	void readsLegacyInput() {
		// given / when
		DrawInspectionRulesMapper.Decoded decoded = mapper.decode(json(1, ""));
		// then
		assertThat(decoded.rules().version()).isEqualTo(1);
		assertThat(decoded.rules().prizes().getFirst().id()).isEqualTo(prizeId);
		assertThat(decoded.replacementRules()).isNull();
	}

	@Test
	@DisplayName("GD-87 버전 2 JSON의 최초 실행·실제 자리·유지 결과를 손실 없이 읽는다")
	void readsReplacementContract() {
		// given
		String additional = """
			,"originalRunId":"%s", "targetSlots":[{"prizeId":"%s","rank":1,"slotNumber":3}],
			"retainedAwards":[{"resultId":"%s","userId":"%s","prizeId":"%s","slotNumber":1}]
			""".formatted(originalId, prizeId, resultId, userId, prizeId);
		// when
		DrawInspectionRulesMapper.Decoded decoded = mapper.decode(json(2, additional));
		// then
		assertThat(decoded.rules().version()).isEqualTo(2);
		assertThat(decoded.replacementRules().getOriginalRunId()).isEqualTo(originalId);
		assertThat(decoded.replacementRules().getTargetSlots()).singleElement().satisfies(slot -> {
			assertThat(slot.getPrizeId()).isEqualTo(prizeId);
			assertThat(slot.getRank()).isEqualTo(1);
			assertThat(slot.getSlotNumber()).isEqualTo(3);
		});
		assertThat(decoded.replacementRules().getRetainedAwards()).singleElement().satisfies(award -> {
			assertThat(award.getResultId()).isEqualTo(resultId);
			assertThat(award.getUserId()).isEqualTo(userId);
			assertThat(award.getPrizeId()).isEqualTo(prizeId);
			assertThat(award.getSlotNumber()).isEqualTo(1);
		});
	}

	@Test
	@DisplayName("손상 JSON·필수 목록 누락·null 항목을 검증 가능한 누락 값으로 복원한다")
	void representsMalformedSnapshots() {
		// given / when / then
		assertThat(mapper.decode("{").rules()).isNull();
		assertThat(mapper.decode("{}").rules()).isNull();
		assertThat(mapper.decode(null).rules()).isNull();
		assertThat(mapper.decode(json(2, ",\"targetSlots\":[null]")).rules()).isNull();
	}

	@Test
	@DisplayName("버전 2의 누락 대상 자리를 임의 생성하지 않는다")
	void doesNotInventMissingSlots() {
		// given / when
		DrawInspectionRulesMapper.Decoded decoded = mapper.decode(json(2, ""));
		// then
		assertThat(decoded.replacementRules().getOriginalRunId()).isNull();
		assertThat(decoded.replacementRules().getTargetSlots()).isEmpty();
		assertThat(decoded.replacementRules().getRetainedAwards()).isEmpty();
	}

	private String json(int version, String extra) {
		return """
			{"version":%d,"weightingEnabled":false,"eventType":"NO_TICKET","membershipRule":"ALL",
			"bronzeWeight":1,"silverWeight":3,"goldWeight":5,"prizes":[{"id":"%s","rank":1,"winnerCount":3}]%s}
			""".formatted(version, prizeId, extra);
	}
}
