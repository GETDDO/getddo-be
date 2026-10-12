package com.getddo.core.ticket.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UseCommandTest {

	@Test
	@DisplayName("호출자가 원본 목록을 바꿔도 요청의 선택은 바뀌지 않고 요청의 목록 자체도 수정할 수 없다")
	void copiesSelections() {
		// given
		List<UseSelection> original = new ArrayList<>(List.of(new UseSelection(TicketGrade.BRONZE, 1)));
		UseCommand command = new UseCommand(UUID.randomUUID(), UUID.randomUUID(), original, "사유");
		// when
		original.add(new UseSelection(TicketGrade.GOLD, 2));
		// then
		assertThat(command.getSelections()).hasSize(1);
		assertThatThrownBy(() -> command.getSelections().add(new UseSelection(TicketGrade.SILVER, 1)))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("선택 목록이 없으면 null로 두어 서비스가 검증하게 한다")
	void keepsNullSelections() {
		// given
		// when
		UseCommand command = new UseCommand(UUID.randomUUID(), UUID.randomUUID(), null, "사유");
		// then
		assertThat(command.getSelections()).isNull();
	}
}
