package com.getddo.db.game;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 기존 응모권 지급 컨텍스트에 게임 저장소만 추가한다. */
@TestConfiguration(proxyBeanMethods = false)
@ComponentScan(basePackages = "com.getddo.db.game")
@EntityScan(basePackages = "com.getddo.db.game")
@EnableJpaRepositories(basePackages = "com.getddo.db.game")
public class GamePersistenceTestConfiguration {
}
