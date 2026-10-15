package com.getddo.api.audit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.getddo.api.support.ApiIntegrationTest;
import com.getddo.core.audit.domain.AuditLogCommand;
import com.getddo.core.audit.service.AuditLogService;
import com.getddo.db.common.util.UuidBinary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 MySQL·기존 기록 서비스·새 MVC 조회와 비공개 데이터 경계를 연결한다. */
@ApiIntegrationTest
class AdminAuditLogIntegrationTest {
	private static final String PATH = "/api/v1/admin/audit-logs";
	private static final UUID ADMIN = id(1);
	private static final UUID USER = id(2);
	private static final UUID INACTIVE = id(3);
	private static final UUID TARGET = id(9);
	private static final String TIME = "2026-10-15T00:00:00Z";
	private final List<UUID> auditIds = new ArrayList<>();

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private AuditLogService writer;
	@Autowired private PlatformTransactionManager transactions;

	@BeforeEach
	void seed() {
		for (int n = 1; n <= 3; n++) jdbc.update("""
				insert into users (id, name, role, status, created_at, updated_at)
				values (?, '감사 조회 시연', ?, ?, '2026-10-15 00:00:00', '2026-10-15 00:00:00')
				""", bytes(id(n)), n == 2 ? "USER" : "ADMIN", n == 3 ? "INACTIVE" : "ACTIVE");
		insert(11, ADMIN, "EVENT_CANCEL", "EVENT", 0,
				"{\"status\":\"OPEN\",\"token\":\"fixture-secret\"}",
				"{\"status\":\"CANCELED\",\"canceledAt\":\"2026-10-15T00:00:00Z\",\"refundedTicketCount\":3,\"email\":\"fixture@example.test\"}");
		insert(12, ADMIN, "NOTIFICATION_JOB_RETRY", "NOTIFICATION_JOB", 0,
				"{\"status\":\"FAILED\",\"attemptCount\":4}", "{\"status\":\"PENDING\",\"attemptBase\":4}");
		insert(13, ADMIN, "UNREVIEWED", "EVENT", 1,
				"{\"status\":\"OPEN\",\"token\":\"fixture-secret\"}", "{\"status\":\"CANCELED\"}");
		insert(14, null, "EVENT_DELETE", "EVENT", 2,
				"{\"deletedAt\":null,\"password\":\"fixture-secret\"}", "{\"deletedAt\":{\"token\":\"fixture-secret\"}}");
		insert(15, INACTIVE, "AWARD_CANCEL", "DRAW_RESULT", 3,
				"{\"isCanceled\":false}", "{\"isCanceled\":\"true\",\"cancellationId\":\"" + TARGET + "\",\"replacementRunId\":[1,2]}");
	}

	@AfterEach
	void clean() {
		for (UUID auditId : auditIds) jdbc.update("delete from audit_logs where id = ?", bytes(auditId));
		for (int n = 1; n <= 3; n++) jdbc.update("delete from users where id = ?", bytes(id(n)));
	}

	@Test
	@DisplayName("목록 기본 페이지·전체 건수·안정 정렬을 보존하고 JSON을 목록에 넣지 않는다")
	void defaultsAndStablePages() throws Exception {
		// given / when / then
		mvc.perform(list()).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.page").value(1)).andExpect(jsonPath("$.data.size").value(20))
				.andExpect(jsonPath("$.data.totalElements").value(5))
				.andExpect(jsonPath("$.data.items[0]", aMapWithSize(8)))
				.andExpect(jsonPath("$.data.items[0].id").value(id(15).toString()))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-10-15T00:00:00.000003Z"))
				.andExpect(jsonPath("$.data.items[0].beforeData").doesNotExist());
		mvc.perform(list().param("size", "2").param("page", "2")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(id(13).toString()))
				.andExpect(jsonPath("$.data.items[1].id").value(id(12).toString()));
		mvc.perform(list().param("size", "2").param("page", "3")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].id").value(id(11).toString()));
	}

	@Test
	@DisplayName("처리자·작업·대상 종류·ID·기간은 AND이고 코드 공백과 빈 필터를 정리한다")
	void combinesFilters() throws Exception {
		// given / when / then
		mvc.perform(list().param("actorId", ADMIN.toString()).param("action", " EVENT_CANCEL ")
				.param("targetType", " EVENT ").param("from", TIME).param("to", "2026-10-15T00:00:00.000001Z"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(id(11).toString()));
		mvc.perform(list().param("action", "EVENT_CANCEL").param("actorId", INACTIVE.toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
		mvc.perform(list().param("action", "   ").param("targetType", "   "))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(5));
		mvc.perform(list().param("action", "UNREVIEWED"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
	}

	@ParameterizedTest
	@CsvSource({"2026-10-15T00:00:00Z,2026-10-15T00:00:00.000001Z,2",
			"2026-10-15T00:00:00.000001Z,2026-10-15T00:00:00.000002Z,1",
			"2026-10-15T00:00:00.000000001Z,2026-10-15T00:00:00.000001001Z,1",
			"2026-10-15T00:00:00.000000001Z,2026-10-15T00:00:00.000001Z,0"})
	@DisplayName("UTC 포함/제외 경계는 마이크로초와 나노초 입력 모두 실제 DB에서 보존된다")
	void preservesInstantBoundaries(String from, String to, int expected) throws Exception {
		// given / when / then
		mvc.perform(list().param("from", from).param("to", to)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(expected));
	}

	@Test
	@DisplayName("기간 한쪽만 지정할 수 있고 범위 밖 큰 페이지는 건수를 보존한다")
	void openBoundariesAndEmptyPages() throws Exception {
		// given / when / then
		mvc.perform(list().param("to", "2026-10-15T00:00:00.000001Z"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
		mvc.perform(list().param("from", "2026-10-15T00:00:00.000003Z"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
		for (String page : List.of("5", "2147483647")) {
			mvc.perform(list().param("page", page).param("size", "100"))
					.andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(5))
					.andExpect(jsonPath("$.data.items", hasSize(0)));
		}
	}

	@Test
	@DisplayName("상세는 확인한 필드만 공개하고 알 수 없는 작업·중첩·잘못된 자료형을 숨긴다")
	void protectsPrivateDataAndPreservesStoredRows() throws Exception {
		// given
		List<Map<String, Object>> before = rows();
		// when / then
		String body = mvc.perform(admin(PATH + "/" + id(11))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data", aMapWithSize(10)))
				.andExpect(jsonPath("$.data.beforeData", aMapWithSize(1)))
				.andExpect(jsonPath("$.data.beforeData.status").value("OPEN"))
				.andExpect(jsonPath("$.data.afterData", aMapWithSize(3)))
				.andExpect(jsonPath("$.data.afterData.refundedTicketCount").value(3))
				.andReturn().getResponse().getContentAsString();
		assertThat(body).doesNotContain("fixture-secret", "fixture@example.test", "token", "email");
		mvc.perform(admin(PATH + "/" + id(13))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.beforeData").value(nullValue()))
				.andExpect(jsonPath("$.data.afterData").value(nullValue()));
		mvc.perform(admin(PATH + "/" + id(14))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.actorId").value(nullValue()))
				.andExpect(jsonPath("$.data.reason").value(nullValue()))
				.andExpect(jsonPath("$.data.requestId").value(nullValue()))
				.andExpect(jsonPath("$.data.beforeData", aMapWithSize(1)))
				.andExpect(jsonPath("$.data.beforeData.deletedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.afterData").value(nullValue()));
		mvc.perform(admin(PATH + "/" + id(15))).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.afterData", aMapWithSize(1)))
				.andExpect(jsonPath("$.data.afterData.cancellationId").value(TARGET.toString()));
		assertThat(rows()).usingRecursiveComparison().isEqualTo(before);
	}

	@Test
	@DisplayName("기존 MANDATORY 기록 서비스를 업무 트랜잭션으로 커밋한 행도 조회한다")
	void readsCommittedExistingWriter() throws Exception {
		// given / when
		UUID written = new TransactionTemplate(transactions).execute(transaction -> writer.record(new AuditLogCommand(
				ADMIN, "AWARD_CANCEL", "DRAW_RESULT", TARGET, "검증 사유",
				Map.of("isCanceled", false), Map.of("isCanceled", true, "cancellationId", TARGET.toString()), "fixture-request")));
		auditIds.add(written);
		// then
		mvc.perform(admin(PATH + "/" + written)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(written.toString()))
				.andExpect(jsonPath("$.data.actorId").value(ADMIN.toString()))
				.andExpect(jsonPath("$.data.reason").value("검증 사유"))
				.andExpect(jsonPath("$.data.requestId").value("fixture-request"))
				.andExpect(jsonPath("$.data.afterData.isCanceled").value(true));
	}

	@Test
	@DisplayName("없는 상세는 AUDIT-003이고 잘못된 UUID는 COMMON-005다")
	void missingAndMalformedDetail() throws Exception {
		// given / when / then
		mvc.perform(admin(PATH + "/" + UUID.randomUUID())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("AUDIT-003"));
		mvc.perform(admin(PATH + "/invalid")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
	}

	@ParameterizedTest
	@CsvSource({"page,0", "page,-1", "size,0", "size,101",
			"from,0999-12-31T23:59:59Z", "to,+10000-01-01T00:00:00Z"})
	@DisplayName("페이지·크기·DB 시각 범위 위반은 AUDIT-001이다")
	void invalidConditions(String key, String value) throws Exception {
		// given / when / then
		mvc.perform(admin(PATH).param(key, value)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("AUDIT-001"));
	}

	@Test
	@DisplayName("기간 역전·동일 경계와 긴 작업·대상 코드를 AUDIT-001로 거절한다")
	void invalidRangesAndText() throws Exception {
		// given / when / then
		for (String from : List.of(TIME, "2026-10-15T00:00:01Z")) {
			mvc.perform(list().param("from", from).param("to", TIME)).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("AUDIT-001"));
		}
		for (String key : List.of("action", "targetType")) {
			mvc.perform(list().param(key, "가".repeat(61))).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("AUDIT-001"));
		}
	}

	@ParameterizedTest
	@CsvSource({"page,abc", "size,2147483648", "actorId,abc", "targetId,abc", "from,not-a-time", "to,2026-02-30"})
	@DisplayName("UUID·시각·숫자 형식 오류는 COMMON-005다")
	void malformedConditions(String key, String value) throws Exception {
		// given / when / then
		mvc.perform(admin(PATH).param(key, value)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
	}

	@Test
	@DisplayName("일반 사용자·비활성 관리자는 두 조회 모두 AUDIT-002다")
	void deniesUnqualifiedActors() throws Exception {
		// given / when / then
		for (String path : List.of(PATH, PATH + "/" + id(11))) {
			mvc.perform(get(path).header("X-User-ID", USER).header("X-User-Role", "USER"))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUDIT-002"));
			mvc.perform(get(path).header("X-User-ID", INACTIVE).header("X-User-Role", "ADMIN"))
					.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUDIT-002"));
		}
	}

	@Test
	@DisplayName("공통 사용자 문맥의 누락·미등록·역할 위장·선택 멤버십 대조를 유지한다")
	void preservesContextErrors() throws Exception {
		// given / when / then
		mvc.perform(get(PATH)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER-003"));
		mvc.perform(get(PATH).header("X-User-ID", UUID.randomUUID()).header("X-User-Role", "ADMIN"))
				.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER-002"));
		mvc.perform(get(PATH).header("X-User-ID", USER).header("X-User-Role", "ADMIN"))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER-004"));
		mvc.perform(admin(PATH).header("X-User-Membership", "vip"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER-006"));
	}

	private static MockHttpServletRequestBuilder admin(String path) {
		return get(path).header("X-User-ID", ADMIN).header("X-User-Role", "ADMIN");
	}

	private static MockHttpServletRequestBuilder list() {
		return admin(PATH).param("targetId", TARGET.toString());
	}

	private static UUID id(int value) {
		return UUID.fromString(String.format(java.util.Locale.ROOT, "00000000-0000-0000-0000-%012d", 20700 + value));
	}

	private static byte[] bytes(UUID id) {
		return UuidBinary.toBytes(id);
	}

	private List<Map<String, Object>> rows() {
		return jdbc.queryForList("select * from audit_logs where target_id = ? order by id", bytes(TARGET));
	}

	private void insert(int number, UUID actor, String action, String targetType, int micros, String before, String after) {
		UUID auditId = id(number);
		auditIds.add(auditId);
		jdbc.update("""
				insert into audit_logs (id, actor_id, action, target_type, target_id, reason, request_id,
				                        created_at, before_data, after_data)
				values (?, ?, ?, ?, ?, null, null, ?, ?, ?)
				""", bytes(auditId), actor == null ? null : bytes(actor), action, targetType, bytes(TARGET),
				java.time.LocalDateTime.ofInstant(Instant.parse(TIME).plusNanos(micros * 1000L), java.time.ZoneOffset.UTC), before, after);
	}
}
