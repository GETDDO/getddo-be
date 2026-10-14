package com.getddo.db.audit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.getddo.core.audit.service.AuditLogService;
import com.getddo.db.audit.entity.AuditLogEntity;
import com.getddo.db.audit.mapper.AuditLogMapper;
import com.getddo.db.audit.repository.AuditLogJpaRepository;
import com.getddo.db.audit.repository.AuditLogRepositoryImpl;
import com.getddo.db.common.config.JpaAuditingConfig;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = AuditLogEntity.class)
@EnableJpaRepositories(basePackageClasses = AuditLogJpaRepository.class)
@ComponentScan(basePackageClasses = AuditLogMapper.class)
@Import({JpaAuditingConfig.class, AuditLogRepositoryImpl.class, AuditLogService.class,
		AuditTransactionTestService.class})
class AuditPersistenceTestConfiguration {

	@Bean
	Clock auditTestClock() {
		return Clock.fixed(Instant.parse("2026-10-01T01:02:03.123456Z"), ZoneOffset.UTC);
	}
}
