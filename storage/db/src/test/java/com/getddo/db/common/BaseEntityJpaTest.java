package com.getddo.db.common;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import com.getddo.db.common.config.JpaAuditingConfig;
import com.getddo.db.common.entity.BaseEntity;
import com.getddo.db.common.entity.BaseUpdatableEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.jpa.properties.hibernate.type.preferred_uuid_jdbc_type=BINARY",
		"spring.flyway.enabled=false"
})
@ContextConfiguration(classes = BaseEntityJpaTest.JpaTestConfig.class)
class BaseEntityJpaTest {

	private static final Instant CREATED = Instant.parse("2026-09-30T14:59:59Z");
	private static final Instant UPDATED = Instant.parse("2026-09-30T15:00:01Z");

	@Autowired
	private EntityManager entityManager;
	@Autowired
	private JdbcTemplate jdbc;
	@Autowired
	private Clock clock;

	@BeforeEach
	void setClock() {
		when(clock.instant()).thenReturn(CREATED);
		when(clock.getZone()).thenReturn(ZoneOffset.UTC);
	}

	@Test
	void storesUuidV7AsBinary16AndReloadsSameValue() {
		CreatedRecord first = new CreatedRecord();
		CreatedRecord second = new CreatedRecord();
		assertThat(first.getId()).isNull();
		entityManager.persist(first);
		entityManager.persist(second);
		entityManager.flush();
		UUID id = first.getId();
		assertThat(id.version()).isEqualTo(7);
		assertThat(second.getId().version()).isEqualTo(7);
		assertThat(second.getId()).isNotEqualTo(id);

		List<byte[]> ids = jdbc.query(
				"select id from auditing_created_record",
				(result, row) -> result.getBytes(1));
		byte[] expected = ByteBuffer.allocate(16)
				.putLong(id.getMostSignificantBits())
				.putLong(id.getLeastSignificantBits()).array();
		assertThat(ids).allSatisfy(value -> assertThat(value).hasSize(16));
		assertThat(ids).anySatisfy(value -> assertThat(value).isEqualTo(expected));
		assertThat(jdbc.queryForObject("""
				select data_type from information_schema.columns
				where table_schema = 'PUBLIC'
				  and table_name = 'AUDITING_CREATED_RECORD'
				  and column_name = 'ID'
				""", String.class)).isEqualTo("BINARY");
		assertThat(jdbc.queryForObject("""
				select character_maximum_length from information_schema.columns
				where table_schema = 'PUBLIC'
				  and table_name = 'AUDITING_CREATED_RECORD'
				  and column_name = 'ID'
				""", Long.class)).isEqualTo(16L);

		entityManager.clear();
		CreatedRecord loaded = entityManager.find(CreatedRecord.class, id);
		assertThat(loaded.getId()).isEqualTo(id);
		assertThat(loaded.getCreatedAt()).isEqualTo(CREATED);
	}

	@Test
	void updatesOnlyModificationTimeUsingTheInjectedClock() {
		MutableRecord record = new MutableRecord("before");
		entityManager.persist(record);
		entityManager.flush();
		UUID id = record.getId();
		entityManager.clear();
		MutableRecord loaded = entityManager.find(MutableRecord.class, id);
		assertThat(loaded.getCreatedAt()).isEqualTo(CREATED);
		assertThat(loaded.getUpdatedAt()).isEqualTo(CREATED);

		when(clock.instant()).thenReturn(UPDATED);
		loaded.changePayload("after");
		entityManager.flush();
		entityManager.clear();
		MutableRecord updated = entityManager.find(MutableRecord.class, id);
		assertThat(updated.getId()).isEqualTo(id);
		assertThat(updated.getCreatedAt()).isEqualTo(CREATED);
		assertThat(updated.getUpdatedAt()).isEqualTo(UPDATED);
		assertThat(updated.payload).isEqualTo("after");
	}

	@Test
	void doesNotChangeModificationTimeWithoutAnEntityChange() {
		MutableRecord record = new MutableRecord("unchanged");
		entityManager.persist(record);
		entityManager.flush();
		UUID id = record.getId();
		entityManager.clear();
		entityManager.find(MutableRecord.class, id);
		when(clock.instant()).thenReturn(UPDATED);
		entityManager.flush();
		entityManager.clear();
		assertThat(entityManager.find(MutableRecord.class, id).getUpdatedAt())
				.isEqualTo(CREATED);
	}

	@Test
	void creationOnlyEntityDoesNotRequireModificationColumn() {
		CreatedRecord record = new CreatedRecord();
		entityManager.persist(record);
		entityManager.flush();
		UUID id = record.getId();
		entityManager.clear();
		assertThat(entityManager.find(CreatedRecord.class, id).getCreatedAt())
				.isEqualTo(CREATED);
		assertThat(jdbc.queryForList("""
				select column_name from information_schema.columns
				where table_schema = 'PUBLIC'
				  and table_name = 'AUDITING_CREATED_RECORD'
				""", String.class)).containsExactlyInAnyOrder("ID", "CREATED_AT");
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@EntityScan(basePackageClasses = BaseEntityJpaTest.class)
	@Import(JpaAuditingConfig.class)
	static class JpaTestConfig {
		@Bean
		Clock auditingTestClock() {
			return mock(Clock.class);
		}
	}

	@Entity(name = "AuditingCreatedRecord")
	@Table(name = "auditing_created_record")
	public static class CreatedRecord extends BaseEntity {
		protected CreatedRecord() { }
	}

	@Entity(name = "AuditingMutableRecord")
	@Table(name = "auditing_mutable_record")
	public static class MutableRecord extends BaseUpdatableEntity {
		@Column(nullable = false)
		private String payload;

		protected MutableRecord() { }

		MutableRecord(String payload) {
			this.payload = payload;
		}

		void changePayload(String payload) {
			this.payload = payload;
		}
	}
}
