package com.getddo.api.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.getddo.api.GetddoBeApplication;
import com.getddo.db.support.MySqlTestConfiguration;

/** 같은 설정의 API 테스트가 Spring 컨텍스트와 그 컨텍스트의 MySQL을 공유한다. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@SpringBootTest(classes = GetddoBeApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MySqlTestConfiguration.class, OpenApiTestFixtures.class})
public @interface ApiIntegrationTest {
}
