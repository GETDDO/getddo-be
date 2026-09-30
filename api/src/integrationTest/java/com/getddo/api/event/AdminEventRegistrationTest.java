package com.getddo.api.event;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.getddo.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiIntegrationTest
class AdminEventRegistrationTest {
	@Autowired
	private MockMvc mvc;

	@Autowired
	private DataSource dataSource;

	@Test
	void registersEventAndEveryPrizeTogether() throws Exception {
		UUID adminId = insertUser("ADMIN");
		long previousEvents = count("events");
		long previousPrizes = count("event_prizes");

		mvc.perform(post("/api/v1/admin/events")
				.header("X-User-ID", adminId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").exists())
				.andExpect(jsonPath("$.data.status").value("SCHEDULED"))
				.andExpect(jsonPath("$.data.startsAt").value("2099-09-30T09:00:00Z"))
				.andExpect(jsonPath("$.data.publicationScheduledAt").value("2099-09-30T10:05:00Z"))
				.andExpect(jsonPath("$.data.createdBy").value(adminId.toString()))
				.andExpect(jsonPath("$.data.imageKey").value("events/autumn.png"))
				.andExpect(jsonPath("$.data.prizes.length()").value(2))
				.andExpect(jsonPath("$.data.prizes[0].rank").value(1))
				.andExpect(jsonPath("$.data.prizes[1].rank").value(2))
				.andExpect(jsonPath("$.data.prizes[0].id").exists())
				.andExpect(jsonPath("$.data.prizeImages[0].imageKey").value("prizes/first.png"));

		assertThat(count("events")).isEqualTo(previousEvents + 1);
		assertThat(count("event_prizes")).isEqualTo(previousPrizes + 2);
		assertThat(countPrizesForAdmin(adminId)).isEqualTo(2);
	}

	@Test
	void duplicateRankAndNonAdminDoNotCreatePartialData() throws Exception {
		UUID adminId = insertUser("ADMIN");
		UUID userId = insertUser("USER");
		long previousEvents = count("events");
		long previousPrizes = count("event_prizes");

		mvc.perform(post("/api/v1/admin/events")
				.header("X-User-ID", adminId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("\"rank\": 2", "\"rank\": 1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EVENT-004"));
		mvc.perform(post("/api/v1/admin/events")
				.header("X-User-ID", userId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("EVENT-006"));

		assertThat(count("events")).isEqualTo(previousEvents);
		assertThat(count("event_prizes")).isEqualTo(previousPrizes);
	}

	@Test
	void missingPrizesInvalidPeriodAndTicketLimitAreRejected() throws Exception {
		UUID adminId = insertUser("ADMIN");
		long previousEvents = count("events");
		long previousPrizes = count("event_prizes");

		mvc.perform(post("/api/v1/admin/events")
				.header("X-User-ID", adminId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("\"prizes\": [", "\"otherPrizes\": [")))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/admin/events")
				.header("X-User-ID", adminId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("2099-09-30T19:00:00+09:00", "2099-09-30T17:00:00+09:00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EVENT-002"));
		mvc.perform(post("/api/v1/admin/events")
				.header("X-User-ID", adminId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("\"maxTicketsPerUser\": 5", "\"maxTicketsPerUser\": 4")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EVENT-003"));

		assertThat(count("events")).isEqualTo(previousEvents);
		assertThat(count("event_prizes")).isEqualTo(previousPrizes);
	}

	@Test
	void databaseFailureWhileSavingPrizeRollsBackEvent() throws Exception {
		UUID adminId = insertUser("ADMIN");
		long previousEvents = count("events");
		long previousPrizes = count("event_prizes");
		String oversizedDescription = "x".repeat(65_536);

		mvc.perform(post("/api/v1/admin/events")
				.header("X-User-ID", adminId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(validRequest().replace("\"name\": \"경품 A\"",
						"\"name\": \"경품 A\", \"description\": \"" + oversizedDescription + "\"")))
				.andExpect(status().is5xxServerError());

		assertThat(count("events")).isEqualTo(previousEvents);
		assertThat(count("event_prizes")).isEqualTo(previousPrizes);
	}

	private UUID insertUser(String role) throws SQLException {
		UUID id = UUID.randomUUID();
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO users (id, name, role, status, membership, created_at, updated_at)
				VALUES (UNHEX(REPLACE(?, '-', '')), '관리자', ?, 'ACTIVE', 'vip', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
				""")) {
			statement.setString(1, id.toString());
			statement.setString(2, role);
			statement.executeUpdate();
		}
		return id;
	}

	private long count(String table) throws SQLException {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
				ResultSet result = statement.executeQuery()) {
			result.next();
			return result.getLong(1);
		}
	}

	private long countPrizesForAdmin(UUID adminId) throws SQLException {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
				SELECT COUNT(*) FROM event_prizes p
				JOIN events e ON e.id = p.event_id
				WHERE e.created_by = UNHEX(REPLACE(?, '-', ''))
				""")) {
			statement.setString(1, adminId.toString());
			try (ResultSet result = statement.executeQuery()) {
				result.next();
				return result.getLong(1);
			}
		}
	}

	private String validRequest() {
		return """
				{
				  "title": "가을 경품 이벤트",
				  "description": "유효한 응모권으로 응모",
				  "imageKey": "events/autumn.png",
				  "eventType": "TICKET",
				  "weightingEnabled": true,
				  "maxTicketsPerUser": 5,
				  "membershipRule": "vip",
				  "startsAt": "2099-09-30T18:00:00+09:00",
				  "endsAt": "2099-09-30T19:00:00+09:00",
				  "prizes": [
				    { "rank": 2, "name": "경품 B", "winnerCount": 3 },
				    { "rank": 1, "name": "경품 A", "imageKey": "prizes/first.png", "winnerCount": 1 }
				  ]
				}
				""";
	}
}
