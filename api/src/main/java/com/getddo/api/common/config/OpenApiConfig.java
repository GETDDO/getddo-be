package com.getddo.api.common.config;

import java.util.Arrays;
import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.getddo.api.common.context.CurrentUser;
import com.getddo.core.user.domain.User;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("GETDDO API")
						.version("0.0.1")
						.description("GETDDO 백엔드 API"));
	}

	/** 현재 사용자를 받는 API에만 시연용 헤더 입력란을 추가한다. */
	@Bean
	public OperationCustomizer currentUserHeaders() {
		return (operation, handlerMethod) -> {
			boolean usesCurrentUser = Arrays.stream(handlerMethod.getMethodParameters())
					.anyMatch(parameter -> parameter.hasParameterAnnotation(CurrentUser.class)
							&& parameter.getParameterType() == User.class);
			if (!usesCurrentUser) {
				return operation;
			}
			operation.addParametersItem(new Parameter().name("X-User-ID").in("header").required(true)
					.description("사전 등록한 사용자 ID").schema(new UUIDSchema()));
			operation.addParametersItem(new Parameter().name("X-User-Role").in("header").required(true)
					.schema(new StringSchema()._enum(List.of("USER", "ADMIN"))));
			operation.addParametersItem(new Parameter().name("X-User-Membership").in("header").required(false)
					.description("USER는 필수, ADMIN은 생략 가능")
					.schema(new StringSchema()._enum(List.of("excellent", "vip", "vvip"))));
			return operation;
		};
	}
}
