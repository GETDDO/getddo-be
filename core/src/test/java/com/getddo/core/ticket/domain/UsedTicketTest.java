package com.getddo.core.ticket.domain;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsedTicketTest {

	@Test
	@DisplayName("응모권 ID와 등급이 모두 있어야 만들 수 있다")
	void requiresTicketIdAndGrade() {
		// given
		UUID ticketId = UUID.randomUUID();
		// when
		// then
		assertThat(new UsedTicket(ticketId, TicketGrade.GOLD).getGrade()).isEqualTo(TicketGrade.GOLD);
		assertThatThrownBy(() -> new UsedTicket(null, TicketGrade.GOLD)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UsedTicket(ticketId, null)).isInstanceOf(NullPointerException.class);
	}
}
