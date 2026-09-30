package com.getddo.db.user;

import java.time.Clock;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.getddo.core.user.service.UserService;
import com.getddo.db.common.config.JpaAuditingConfig;
import com.getddo.db.user.entity.UserEntity;
import com.getddo.db.user.mapper.UserMapper;
import com.getddo.db.user.repository.UserJpaRepository;
import com.getddo.db.user.repository.UserRepositoryImpl;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = UserEntity.class)
@EnableJpaRepositories(basePackageClasses = UserJpaRepository.class)
@ComponentScan(basePackageClasses = UserMapper.class)
@Import({JpaAuditingConfig.class, UserRepositoryImpl.class, UserService.class})
class UserPersistenceTestConfiguration {

	@Bean
	Clock userTestClock() {
		return Clock.systemUTC();
	}
}
