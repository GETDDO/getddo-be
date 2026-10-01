package com.getddo.db.common;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.getddo.db.common.AuditingTestFixtures.CreatedRecord;
import com.getddo.db.common.AuditingTestFixtures.MutableRecord;
import com.getddo.db.support.MySqlTestConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"spring.flyway.enabled=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = AuditingTestFixtures.class)
@ActiveProfiles("test")
@Import(MySqlTestConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BaseEntityJpaTest {

	private static final Instant CREATED = Instant.parse("2026-09-30T14:59:59.999999600Z");
	private static final Instant UPDATED = Instant.parse("2026-09-30T15:00:01.123456789Z");
	private static final Instant EXPECTED_CREATED = Instant.parse("2026-09-30T14:59:59.999999Z");
	private static final Instant EXPECTED_UPDATED = Instant.parse("2026-09-30T15:00:01.123456Z");

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
	@DisplayName("UUID v7을 binary(16)으로 저장하고 동일한 ID와 생성 시각을 조회한다")
	void storesUuidV7AsBinary16AndReloadsSameValue() {
		// given
		CreatedRecord first = new CreatedRecord();
		CreatedRecord second = new CreatedRecord();
		assertThat(first.getId()).isNull();
		// when
		entityManager.persist(first);
		entityManager.persist(second);
		entityManager.flush();
		// then
		UUID id = first.getId();
		assertThat(id.version()).isEqualTo(7);
		assertThat(second.getId().version()).isEqualTo(7);
		assertThat(second.getId()).isNotEqualTo(id);
		assertThat(first.getCreatedAt()).isEqualTo(EXPECTED_CREATED);

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
				where table_schema = database()
				  and table_name = 'auditing_created_record'
				  and column_name = 'id'
				""", String.class)).isEqualTo("binary");
		assertThat(jdbc.queryForObject("""
				select character_maximum_length from information_schema.columns
				where table_schema = database()
				  and table_name = 'auditing_created_record'
				  and column_name = 'id'
				""", Long.class)).isEqualTo(16L);

		entityManager.clear();
		CreatedRecord loaded = entityManager.find(CreatedRecord.class, id);
		assertThat(loaded.getId()).isEqualTo(id);
		assertThat(loaded.getCreatedAt()).isEqualTo(first.getCreatedAt());
	}

	@Test
	@DisplayName("Clock이 진행된 뒤 Entity를 변경하면 수정 시각만 갱신한다")
	void updatesOnlyModificationTimeUsingTheInjectedClock() {
		// given
		MutableRecord record = new MutableRecord("before");
		entityManager.persist(record);
		entityManager.flush();
		assertThat(record.getCreatedAt()).isEqualTo(EXPECTED_CREATED);
		assertThat(record.getUpdatedAt()).isEqualTo(EXPECTED_CREATED);
		UUID id = record.getId();
		entityManager.clear();
		MutableRecord loaded = entityManager.find(MutableRecord.class, id);
		assertThat(loaded.getCreatedAt()).isEqualTo(record.getCreatedAt());
		assertThat(loaded.getUpdatedAt()).isEqualTo(record.getUpdatedAt());

		// when
		when(clock.instant()).thenReturn(UPDATED);
		loaded.changePayload("after");
		entityManager.flush();
		assertThat(loaded.getCreatedAt()).isEqualTo(EXPECTED_CREATED);
		assertThat(loaded.getUpdatedAt()).isEqualTo(EXPECTED_UPDATED);
		entityManager.clear();
		// then
		MutableRecord updated = entityManager.find(MutableRecord.class, id);
		assertThat(updated.getId()).isEqualTo(id);
		assertThat(updated.getCreatedAt()).isEqualTo(loaded.getCreatedAt());
		assertThat(updated.getUpdatedAt()).isEqualTo(loaded.getUpdatedAt());
		assertThat(updated.payload).isEqualTo("after");
	}

	@Test
	@DisplayName("Entity 변경이 없으면 Clock이 진행되어도 수정 시각을 유지한다")
	void doesNotChangeModificationTimeWithoutAnEntityChange() {
		// given
		MutableRecord record = new MutableRecord("unchanged");
		entityManager.persist(record);
		entityManager.flush();
		UUID id = record.getId();
		entityManager.clear();
		entityManager.find(MutableRecord.class, id);
		// when
		when(clock.instant()).thenReturn(UPDATED);
		entityManager.flush();
		entityManager.clear();
		// then
		assertThat(entityManager.find(MutableRecord.class, id).getUpdatedAt())
				.isEqualTo(EXPECTED_CREATED);
	}

	@Test
	@DisplayName("생성 시각 전용 Entity는 수정 시각 컬럼 없이 저장된다")
	void creationOnlyEntityDoesNotRequireModificationColumn() {
		// given
		CreatedRecord record = new CreatedRecord();
		// when
		entityManager.persist(record);
		entityManager.flush();
		UUID id = record.getId();
		entityManager.clear();
		// then
		assertThat(entityManager.find(CreatedRecord.class, id).getCreatedAt())
				.isEqualTo(EXPECTED_CREATED);
		assertThat(jdbc.queryForList("""
				select column_name from information_schema.columns
				where table_schema = database()
				  and table_name = 'auditing_created_record'
				""", String.class)).containsExactlyInAnyOrder("id", "created_at");
	}
}
