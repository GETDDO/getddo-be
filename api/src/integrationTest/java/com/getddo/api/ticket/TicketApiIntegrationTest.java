package com.getddo.api.ticket;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.getddo.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiIntegrationTest
class TicketApiIntegrationTest {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final Duration MARGIN = Duration.ofDays(30);
	private static final YearMonth CURRENT_MONTH = YearMonth.of(2026, 10);
	private static final YearMonth PAST_MONTH = YearMonth.of(2026, 9);
	private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000201");
	private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000202");
	private static final UUID CURRENT_WALLET = UUID.fromString("00000000-0000-0000-0000-000000000211");
	private static final UUID PAST_WALLET = UUID.fromString("00000000-0000-0000-0000-000000000212");
	private static final UUID OTHER_WALLET = UUID.fromString("00000000-0000-0000-0000-000000000213");
	private static final UUID OLDEST = UUID.fromString("00000000-0000-0000-0000-000000000221");
	private static final UUID MIDDLE = UUID.fromString("00000000-0000-0000-0000-000000000222");
	private static final UUID NEWEST = UUID.fromString("00000000-0000-0000-0000-000000000223");
	private static final UUID OTHER_GRANT = UUID.fromString("00000000-0000-0000-0000-000000000224");
	private static final Instant T1 = Instant.parse("2026-09-01T01:00:00Z");
	private static final Instant T2 = Instant.parse("2026-09-02T01:00:00Z");
	private static final Instant T3 = Instant.parse("2026-09-03T01:00:00Z");

	@Autowired private MockMvc mvc;
	@Autowired private JdbcTemplate jdbc;
	@Autowired private ObjectMapper mapper;

	/**
	 * 서버는 실제 시계로 만료를 판정한다. 테스트가 월 경계에 걸려도 결과가 바뀌지 않도록 사용 가능 지갑은 지금부터
	 * 30일 뒤, 만료 지갑은 30일 전에 만료되게 둔다(만료 지갑의 저장 상태는 ACTIVE). 사용 가능 지갑에는 지급 이력 3건을 둔다.
	 */
	@BeforeEach
	void seed() {
		Instant now = Instant.now();
		insertUser(USER);
		insertUser(OTHER);
		insertWallet(CURRENT_WALLET, USER, CURRENT_MONTH, now.plus(MARGIN), 3);
		insertWallet(PAST_WALLET, USER, PAST_MONTH, now.minus(MARGIN), 5);
		insertWallet(OTHER_WALLET, OTHER, CURRENT_MONTH, now.plus(MARGIN), 9);
		insertGrant(OLDEST, CURRENT_WALLET, USER, T1, 1);
		insertGrant(MIDDLE, CURRENT_WALLET, USER, T2, 2);
		insertGrant(NEWEST, CURRENT_WALLET, USER, T3, 3);
		insertGrant(OTHER_GRANT, OTHER_WALLET, OTHER, T3, 1);
	}

	@AfterEach
	void clean() {
		jdbc.update("delete from ticket_ledger where user_id in (?, ?)", bytes(USER), bytes(OTHER));
		jdbc.update("delete from ticket_wallets where user_id in (?, ?)", bytes(USER), bytes(OTHER));
		jdbc.update("delete from users where id in (?, ?)", bytes(USER), bytes(OTHER));
	}

	@Test
	@DisplayName("T01은 본인 지갑만 만료월 최신순으로 반환하고 만료 시각이 지난 지갑은 EXPIRED로 잔액에서 뺀다")
	void returnsOwnWalletsWithAvailableBalance() throws Exception {
		// given: seed()가 이번 달·지난달 지갑과 타인 지갑을 준비한다.

		// when / then
		mvc.perform(get("/api/v1/tickets/wallets/me").headers(userHeaders(USER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.availableBalance").value(3))
				.andExpect(jsonPath("$.data.wallets.length()").value(2))
				.andExpect(jsonPath("$.data.wallets[0].id").value(CURRENT_WALLET.toString()))
				.andExpect(jsonPath("$.data.wallets[0].expiryMonth").value("2026-10"))
				.andExpect(jsonPath("$.data.wallets[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.wallets[1].id").value(PAST_WALLET.toString()))
				.andExpect(jsonPath("$.data.wallets[1].expiryMonth").value("2026-09"))
				.andExpect(jsonPath("$.data.wallets[1].status").value("EXPIRED"))
				.andExpect(jsonPath("$.data.wallets[1].balance").value(5));
	}

	@Test
	@DisplayName("T02는 본인 이력을 최신순 커서로 넘기며 마지막 페이지에서 다음 커서가 없다")
	void pagesOwnLedgerWithCursor() throws Exception {
		// given: seed()가 본인 지급 3건과 타인 지급 1건을 준비한다.

		// when / then
		String body = mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER)).param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(3))
				.andExpect(jsonPath("$.data.items[0].id").value(NEWEST.toString()))
				.andExpect(jsonPath("$.data.items[0].createdAt").value("2026-09-03T01:00:00Z"))
				.andExpect(jsonPath("$.data.items[0].transactionType").value("GRANT"))
				.andExpect(jsonPath("$.data.items[1].id").value(MIDDLE.toString()))
				.andReturn().getResponse().getContentAsString();
		String cursor = mapper.readTree(body).path("data").path("nextCursor").asString();
		assertThat(cursor).isNotBlank();
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER))
						.param("size", "2").param("cursor", cursor))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(OLDEST.toString()))
				.andExpect(jsonPath("$.data.nextCursor").value(nullValue()));
	}

	@Test
	@DisplayName("T02는 거래 유형과 오프셋이 있는 [from, to) 기간으로 거른다")
	void filtersByTypeAndPeriod() throws Exception {
		// given: seed()가 9/1·9/2·9/3 01:00Z 지급을 준비한다.

		// when / then
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER))
						.param("from", "2026-09-02T10:00:00+09:00").param("to", "2026-09-03T01:00:00Z"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(1))
				.andExpect(jsonPath("$.data.items[0].id").value(MIDDLE.toString()));
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER)).param("transactionType", "SPEND"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalElements").value(0))
				.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	@DisplayName("T02의 잘못된 개수·커서·기간은 400 TICKET-004, 해석할 수 없는 값은 400 COMMON-005로 거절한다")
	void rejectsInvalidLedgerQuery() throws Exception {
		// given: seed()가 사용자를 준비한다.

		// when / then
		for (String[] parameter : new String[][] {{"size", "0"}, {"size", "101"}, {"cursor", "bad cursor"}}) {
			mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER)).param(parameter[0], parameter[1]))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("TICKET-004"));
		}
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER))
						.param("from", "2026-09-03T00:00:00Z").param("to", "2026-09-02T00:00:00Z"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("TICKET-004"));
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(USER)).param("from", "2026-09-01T00:00:00"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
	}

	@Test
	@DisplayName("사용자 헤더가 없거나 등록되지 않은 사용자면 공통 오류로 거절한다")
	void rejectsMissingOrUnknownUser() throws Exception {
		// given: seed()가 사용자를 준비한다.

		// when / then
		mvc.perform(get("/api/v1/tickets/wallets/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-003"));
		mvc.perform(get("/api/v1/tickets/ledger/me").headers(userHeaders(UUID.randomUUID())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("USER-002"));
	}

	private HttpHeaders userHeaders(UUID userId) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-User-ID", userId.toString());
		headers.set("X-User-Role", "USER");
		headers.set("X-User-Membership", "vip");
		return headers;
	}

	private void insertUser(UUID id) {
		LocalDateTime now = utc(Instant.now());
		jdbc.update("""
				insert into users (id, name, role, status, membership, updated_at, created_at)
				values (?, 'test', 'USER', 'ACTIVE', 'VIP', ?, ?)
				""", bytes(id), now, now);
	}

	/** 저장 상태는 ACTIVE로 두고 만료 시각만 지정한 지갑. */
	private void insertWallet(UUID id, UUID userId, YearMonth month, Instant expiresAtInstant, long balance) {
		LocalDateTime validFrom = utc(month.atDay(1).atStartOfDay(KST).toInstant());
		LocalDateTime expiresAt = utc(expiresAtInstant);
		jdbc.update("""
				insert into ticket_wallets
				  (id, user_id, expiry_month, valid_from, expires_at, balance, status, version, created_at, updated_at)
				values (?, ?, ?, ?, ?, ?, 'ACTIVE', 3, ?, ?)
				""", bytes(id), bytes(userId), LocalDate.from(month.atDay(1)), validFrom, expiresAt, balance,
				validFrom, validFrom);
	}

	private void insertGrant(UUID id, UUID walletId, UUID userId, Instant createdAt, long walletVersion) {
		jdbc.update("""
				insert into ticket_ledger
				  (id, wallet_id, user_id, transaction_type, quantity, idempotency_key, reason, created_at,
				   balance_after, wallet_version, expires_at)
				values (?, ?, ?, 'GRANT', 1, ?, '테스트 지급', ?, ?, ?, ?)
				""", bytes(id), bytes(walletId), bytes(userId), "GRANT:TEST:" + id, utc(createdAt),
				walletVersion, walletVersion, utc(createdAt.plusSeconds(86_400 * 30L)));
	}

	/** Entity가 Instant를 저장하는 방식과 같게 UTC 기준 날짜·시각으로 넣는다. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits())
				.putLong(id.getLeastSignificantBits()).array();
	}
}
