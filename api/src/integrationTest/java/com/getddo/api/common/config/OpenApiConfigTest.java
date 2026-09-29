package com.getddo.api.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.getddo.api.support.ApiIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ApiIntegrationTest
class OpenApiConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	@DisplayName("Swagger UI와 문서 설정을 제공한다")
	void servesSwaggerUi() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/swagger-ui/index.html"));
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("swagger-ui-bundle.js")));
		mockMvc.perform(get("/v3/api-docs/swagger-config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("/v3/api-docs"));
	}

	@Test
	@DisplayName("Swagger 어노테이션 없이 요청·응답 문서를 생성한다")
	void generatesDocumentWithoutSwaggerAnnotations() throws Exception {
		String body = mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.info.title").value("GETDDO API"))
				.andExpect(jsonPath("$.info.version").value("0.0.1"))
				.andReturn().getResponse().getContentAsString();

		JsonNode document = objectMapper.readTree(body);
		JsonNode operation = document.path("paths").path("/test/openapi/{id}").path("post");
		assertThat(operation.isMissingNode()).isFalse();
		JsonNode parameter = operation.path("parameters").get(0);
		assertThat(parameter.path("name").asString()).isEqualTo("id");
		assertThat(parameter.path("in").asString()).isEqualTo("path");
		assertThat(parameter.path("required").asBoolean()).isTrue();

		JsonNode request = resolveSchema(document, operation.path("requestBody")
				.path("content").path("application/json").path("schema"));
		assertThat(request.path("properties").path("name").path("type").asString())
				.isEqualTo("string");

		JsonNode response = resolveSchema(document, operation.path("responses").path("200")
				.path("content").path("application/json").path("schema"));
		JsonNode properties = response.path("properties");
		assertThat(properties.path("success").path("type").asString()).isEqualTo("boolean");
		assertThat(properties.path("code").path("type").asString()).isEqualTo("string");
		assertThat(properties.path("message").path("type").asString()).isEqualTo("string");
		JsonNode data = resolveSchema(document, properties.path("data"));
		assertThat(data.path("properties").path("id").path("type").asString()).isEqualTo("integer");
		assertThat(data.path("properties").path("name").path("type").asString()).isEqualTo("string");
	}

	private JsonNode resolveSchema(JsonNode document, JsonNode schema) {
		String reference = schema.path("$ref").asString();
		assertThat(reference).startsWith("#/components/schemas/");
		return document.at(reference.substring(1));
	}
}
