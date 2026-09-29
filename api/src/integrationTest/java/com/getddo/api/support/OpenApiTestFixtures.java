package com.getddo.api.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.response.ResponseEnvelope;

/** API 테스트 컨텍스트에만 문서 검증용 Controller를 등록한다. */
@TestConfiguration(proxyBeanMethods = false)
@Import(OpenApiTestFixtures.DocumentationController.class)
public class OpenApiTestFixtures {

	// Swagger 어노테이션 없이 매핑과 타입으로 문서를 생성한다.
	@TestComponent
	@RestController
	static class DocumentationController {

		@PostMapping(value = "/test/openapi/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
		ResponseEnvelope<DocumentationResponse> create(
				@PathVariable("id") long id, @RequestBody DocumentationRequest request
		) {
			return ResponseEnvelope.success(new DocumentationResponse(id, request.name()));
		}
	}

	record DocumentationRequest(String name) {}

	record DocumentationResponse(long id, String name) {}
}
