package com.getddo.db.audit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.service.AuditLogService;
import com.getddo.db.audit.entity.AuditLogEntity;
import com.getddo.db.support.MySqlTestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = AuditPersistenceTestConfiguration.class, properties = {
		"spring.jpa.hibernate.ddl-auto=validate",
		"spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
		"spring.flyway.enabled=true"
})
@ActiveProfiles("test")
@Import(MySqlTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuditLogIntegrationTest {

	@Autowired private AuditLogService auditLogService;
	@Autowired private AuditTransactionTestService business;
	@Autowired private TransactionTemplate transaction;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private EntityManager entityManager;
	private UUID actorId;
	private UUID targetId;

	@BeforeEach
	void seed() {
		actorId = UUID.randomUUID();
		targetId = UUID.randomUUID();
		jdbc.update("""
				insert into users (id, name, role, status, created_at, updated_at)
				values (unhex(replace(?, '-', '')), '변경 전', 'ADMIN', 'ACTIVE',
				        '2026-10-01 00:00:00', '2026-10-01 00:00:00')
				""", actorId.toString());
	}

	@AfterEach
	void clean() {
		jdbc.update("delete from audit_logs where target_id = unhex(replace(?, '-', ''))", targetId.toString());
		jdbc.update("delete from users where id = unhex(replace(?, '-', ''))", actorId.toString());
	}

	@Test
	@DisplayName("업무와 로그를 함께 commit하고 JSON 객체·배열·null 및 UTC 마이크로초를 저장한다")
	void commitsBusinessAndJsonSnapshot() {
		// given
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("state", "BEFORE");
		before.put("optional", null);
		Map<String, Object> after = Map.of("state", "AFTER", "nested", Map.of("enabled", true),
				"items", Arrays.asList(1, "two", null), "amount", new BigDecimal("12.345"));
		AuditLogCommand command = new AuditLogCommand(actorId, "TEST_CHANGE", "TEST_TARGET", targetId,
				"테스트 변경 사유", before, after, "test-request-55");
		// when: 실제 업무 트랜잭션이 종료된 후 JDBC로 다시 조회한다.
		UUID id = business.changeAndRecord(actorId, command, false);
		// then
		assertThat(userName()).isEqualTo("변경 후");
		assertThat(id.version()).isEqualTo(7);
		Map<String, Object> row = jdbc.queryForMap("""
				select lower(hex(id)) as id, lower(hex(actor_id)) as actor,
				       lower(hex(target_id)) as target, action, target_type, reason, request_id,
				       date_format(created_at, '%Y-%m-%d %H:%i:%s.%f') as created,
				       json_type(before_data) as before_type, json_type(after_data) as after_type,
				       json_unquote(json_extract(before_data, '$.state')) as before_state,
				       json_type(json_extract(before_data, '$.optional')) as optional_type,
				       json_unquote(json_extract(after_data, '$.state')) as after_state,
				       json_extract(after_data, '$.nested.enabled') as enabled,
				       json_length(json_extract(after_data, '$.items')) as item_count,
				       json_type(json_extract(after_data, '$.items[2]')) as last_type,
				       json_unquote(json_extract(after_data, '$.amount')) as amount
				from audit_logs where id = unhex(replace(?, '-', ''))
				""", id.toString());
		assertThat(row).containsEntry("id", hex(id)).containsEntry("actor", hex(actorId))
				.containsEntry("target", hex(targetId)).containsEntry("action", "TEST_CHANGE")
				.containsEntry("target_type", "TEST_TARGET").containsEntry("reason", "테스트 변경 사유")
				.containsEntry("request_id", "test-request-55").containsEntry("created", "2026-10-01 01:02:03.123456")
				.containsEntry("before_type", "OBJECT").containsEntry("after_type", "OBJECT")
				.containsEntry("before_state", "BEFORE").containsEntry("after_state", "AFTER")
				.containsEntry("optional_type", "NULL").containsEntry("last_type", "NULL")
				.containsEntry("enabled", "true").containsEntry("amount", "12.345");
		assertThat(((Number) row.get("item_count")).intValue()).isEqualTo(3);
		transaction.executeWithoutResult(status -> {
			AuditLogEntity loaded = entityManager.find(AuditLogEntity.class, id);
			assertThat(loaded.getActorId()).isEqualTo(actorId);
			assertThat(loaded.getBeforeData()).isEqualTo(before);
			assertThat(loaded.getAfterData()).containsEntry("state", "AFTER");
			assertThat(loaded.getCreatedAt()).isEqualTo(Instant.parse("2026-10-01T01:02:03.123456Z"));
		});
	}

	@Test
	@DisplayName("시스템 actor와 선택 데이터의 null을 SQL NULL로 저장한다")
	void storesSystemActorAndSqlNulls() {
		// when
		UUID id = transaction.execute(status -> auditLogService.record(command(null, null)));
		// then
		Map<String, Object> row = jdbc.queryForMap("""
				select actor_id, reason, before_data, after_data, request_id from audit_logs
				where id = unhex(replace(?, '-', ''))
				""", id.toString());
		assertThat(row.values()).containsOnlyNulls();
	}

	@Test
	@DisplayName("업무 실패 시 이미 flush한 감사 로그와 업무 변경을 함께 rollback한다")
	void rollsBackOnBusinessFailure() {
		// when / then
		assertThatThrownBy(() -> business.changeAndRecord(actorId, command(actorId, null), true))
				.isInstanceOf(IllegalStateException.class).hasMessage("test business failure");
		assertThat(userName()).isEqualTo("변경 전");
		assertThat(logCount()).isZero();
	}

	@Test
	@DisplayName("존재하지 않는 처리자의 FK 오류로 감사 저장에 실패하면 업무도 rollback한다")
	void rollsBackOnAuditFailure() {
		// given
		AuditLogCommand command = command(UUID.randomUUID(), null);
		// when / then
		assertThatThrownBy(() -> business.changeAndRecord(actorId, command, false))
				.isInstanceOf(RuntimeException.class).hasStackTraceContaining("fk_audit_logs_1");
		assertThat(userName()).isEqualTo("변경 전");
		assertThat(logCount()).isZero();
	}

	@Test
	@DisplayName("트랜잭션 없이 기록하면 저장 전에 거절한다")
	void requiresCallerTransaction() {
		// when / then
		assertThatThrownBy(() -> auditLogService.record(command(actorId, null)))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(logCount()).isZero();
	}

	@Test
	@DisplayName("같은 requestId는 중복 방지 키가 아니며 호출마다 새 기록을 추가한다")
	void requestIdDoesNotDeduplicate() {
		// given / when
		UUID first = transaction.execute(status -> auditLogService.record(command(actorId, "same-request")));
		UUID second = transaction.execute(status -> auditLogService.record(command(actorId, "same-request")));
		// then
		assertThat(first).isNotEqualTo(second);
		assertThat(logCount()).isEqualTo(2);
	}

	private AuditLogCommand command(UUID actor, String requestId) {
		return new AuditLogCommand(actor, "TEST_CHANGE", "TEST_TARGET", targetId, null, null, null, requestId);
	}

	private String userName() {
		return jdbc.queryForObject("select name from users where id = unhex(replace(?, '-', ''))",
				String.class, actorId.toString());
	}

	private int logCount() {
		return jdbc.queryForObject("select count(*) from audit_logs where target_id = unhex(replace(?, '-', ''))",
				Integer.class, targetId.toString());
	}

	private String hex(UUID id) {
		return id.toString().replace("-", "");
	}
}
