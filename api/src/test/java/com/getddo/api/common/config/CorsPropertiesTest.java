package com.getddo.api.common.config;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.getddo.core.user.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsPropertiesTest {

	private static final String ORIGIN = "https://frontend.example.test";
	private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
			.withInitializer(new ConfigDataApplicationContextInitializer())
			.withUserConfiguration(CorsConfigTest.TestApplication.class)
			.withBean(UserRepository.class, () -> id -> Optional.empty());

	@ParameterizedTest
	@ValueSource(strings = {"", "https://frontend.example.test,https://demo.example.test:8443"})
	@DisplayName("실제 YAML이 환경변수의 빈 값과 여러 origin을 정확한 허용 목록으로 바인딩한다")
	void bindsOriginsFromEnvironment(String origins) {
		// given
		// YAML 속성을 직접 덮어쓰지 않고 환경변수 → YAML placeholder → 설정 Bean 경로를 검증한다.
		runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
				new SystemEnvironmentPropertySource("cors-test-env", Map.of("CORS_ALLOWED_ORIGINS", origins))))
				.run(context -> {
					// when / then
					assertThat(context).hasSingleBean(FilterRegistrationBean.class);
					var mvc = MockMvcBuilders.webAppContextSetup(context)
							.addFilters(context.getBean(FilterRegistrationBean.class).getFilter()).build();
					for (String origin : new String[] {ORIGIN, "https://demo.example.test:8443"}) {
						var result = mvc.perform(options("/api/v1/users/me").header("Origin", origin)
								.header("Access-Control-Request-Method", "GET")
								.header("Access-Control-Request-Headers", "X-User-ID"));
						if (origins.isEmpty()) {
							result.andExpect(status().isForbidden())
									.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
						} else {
							result.andExpect(status().isOk())
									.andExpect(header().string("Access-Control-Allow-Origin", origin))
									.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
									.andExpect(header().doesNotExist("Access-Control-Expose-Headers"));
						}
					}
				});
	}

	@Test
	@DisplayName("origin 환경변수가 없으면 애플리케이션을 시작하되 다른 origin은 허용하지 않는다")
	void missingOriginsDenyCrossOriginRequests() {
		// given / when / then
		// 개발자가 셸에 설정한 CORS_ALLOWED_ORIGINS가 '미설정' 시나리오에 섞이지 않도록 격리한다.
		runner.withInitializer(context -> context.getEnvironment().getPropertySources()
				.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)).run(context -> {
			assertThat(context).hasSingleBean(FilterRegistrationBean.class);
			MockMvcBuilders.webAppContextSetup(context)
					.addFilters(context.getBean(FilterRegistrationBean.class).getFilter()).build()
					.perform(options("/api/v1/users/me").header("Origin", ORIGIN)
							.header("Access-Control-Request-Method", "GET"))
					.andExpect(status().isForbidden())
					.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		});
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	@DisplayName("wildcard origin은 credentials 설정과 관계없이 시작 시 거절한다")
	void rejectsWildcardOriginAtStartup(boolean credentials) {
		// given / when / then
		runner.withPropertyValues("CORS_ALLOWED_ORIGINS=*", "getddo.cors.allow-credentials=" + credentials)
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
							.hasStackTraceContaining("getddo.cors.allowed-origins");
				});
	}

	@Test
	@DisplayName("명시한 credentials·메서드·요청 헤더·노출 헤더 설정을 응답에 적용한다")
	void bindsExplicitPolicy() {
		// given
		runner.withPropertyValues("CORS_ALLOWED_ORIGINS=" + ORIGIN,
				"getddo.cors.allow-credentials=true", "getddo.cors.allowed-methods=DELETE",
				"getddo.cors.allowed-headers=Content-Type", "getddo.cors.exposed-headers=Retry-After")
				.run(context -> {
					// when / then
					assertThat(context).hasSingleBean(FilterRegistrationBean.class);
					var mvc = MockMvcBuilders.webAppContextSetup(context)
							.addFilters(context.getBean(FilterRegistrationBean.class).getFilter()).build();
					mvc.perform(options("/api/v1/users/me").header("Origin", ORIGIN)
								.header("Access-Control-Request-Method", "DELETE")
								.header("Access-Control-Request-Headers", "Content-Type"))
							.andExpect(status().isOk())
							.andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
							.andExpect(header().string("Access-Control-Allow-Methods", "DELETE"))
							.andExpect(header().string("Access-Control-Allow-Headers", "Content-Type"))
							.andExpect(header().string("Access-Control-Allow-Credentials", "true"))
							.andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
					mvc.perform(options("/api/v1/users/me").header("Origin", ORIGIN)
								.header("Access-Control-Request-Method", "GET"))
							.andExpect(status().isForbidden());
				});
	}
}
