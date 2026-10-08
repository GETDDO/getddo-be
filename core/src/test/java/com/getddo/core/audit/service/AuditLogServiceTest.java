package com.getddo.core.audit.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.getddo.core.audit.domain.AuditLogCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogServiceTest {

	@Test
	@DisplayName("입력한 기록을 저장소에 전달하고 저장소가 발급한 감사 ID를 반환한다")
	void returnsStoredId() {
		// given
		UUID id = UUID.randomUUID();
		AuditLogCommand command = command();
		AtomicReference<AuditLogCommand> stored = new AtomicReference<>();
		AuditLogService service = new AuditLogService(input -> {
			stored.set(input);
			return id;
		});
		// when
		UUID result = service.record(command);
		// then
		assertThat(result).isEqualTo(id);
		assertThat(stored.get()).isSameAs(command);
	}

	@Test
	@DisplayName("저장 실패를 삼키지 않고 업무 호출자에게 전파한다")
	void propagatesStorageFailure() {
		// given
		RuntimeException failure = new IllegalStateException("test storage failure");
		AuditLogService service = new AuditLogService(command -> { throw failure; });
		// when / then
		assertThatThrownBy(() -> service.record(command())).isSameAs(failure);
	}

	private AuditLogCommand command() {
		return new AuditLogCommand(null, "TEST_CHANGE", "TEST", UUID.randomUUID(), null, null, null, null);
	}
}
