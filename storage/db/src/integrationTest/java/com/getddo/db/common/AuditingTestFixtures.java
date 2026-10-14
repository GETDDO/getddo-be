package com.getddo.db.common;

import java.time.Clock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.getddo.db.common.config.JpaAuditingConfig;
import com.getddo.db.common.entity.BaseEntity;
import com.getddo.db.common.entity.BaseUpdatableEntity;

import static org.mockito.Mockito.mock;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = AuditingTestFixtures.class)
@Import(JpaAuditingConfig.class)
class AuditingTestFixtures {

	@Bean
	Clock auditingTestClock() {
		return mock(Clock.class);
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
		String payload;

		protected MutableRecord() { }

		MutableRecord(String payload) {
			this.payload = payload;
		}

		void changePayload(String payload) {
			this.payload = payload;
		}
	}
}
