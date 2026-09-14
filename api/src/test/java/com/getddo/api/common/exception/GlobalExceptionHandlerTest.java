package com.getddo.api.common.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.getddo.api.common.response.ResponseEnvelope;
import com.getddo.core.exception.BusinessException;
import com.getddo.core.exception.ErrorCode;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 테스트 전용 Controller와 예외 처리기만 연결해 실제 HTTP 응답 형식을 검증한다.
 *
 * <p>Spring Boot 전체 컨텍스트를 시작하지 않으므로 DB 연결 없이 실행한다.
 * 업무 Service나 저장소의 동작이 아니라 JSON 직렬화, 예외 분류, HTTP 상태·헤더 보존이 검증 대상이다.</p>
 */
class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void successContainsResponseData() throws Exception {
		mockMvc.perform(get("/test/success"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.code").value("SUCCESS"))
				.andExpect(jsonPath("$.message").value("성공했습니다."))
				.andExpect(jsonPath("$.data.name").value("sample"));
	}

	@Test
	void successCanHaveNoData() throws Exception {
		mockMvc.perform(get("/test/empty"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value(nullValue()));
	}

	@Test
	void businessErrorPreservesItsStatusAndCode() throws Exception {
		mockMvc.perform(get("/test/business"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("TEST-001"))
				.andExpect(jsonPath("$.message").value("이미 처리된 요청입니다."))
				.andExpect(jsonPath("$.data").value(nullValue()));
	}

	@Test
	void invalidDtoReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-002"));
	}

	@Test
	void missingParameterReturnsBadRequestInsteadOfServerError() throws Exception {
		mockMvc.perform(get("/test/parameter"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-002"));
	}

	@Test
	void missingHeaderReturnsBadRequest() throws Exception {
		mockMvc.perform(get("/test/header"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-002"));
	}

	@Test
	void malformedJsonReturnsFormatError() throws Exception {
		mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
	}

	@Test
	void invalidParameterTypeReturnsFormatError() throws Exception {
		mockMvc.perform(get("/test/parameter").param("id", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("COMMON-005"));
	}

	// 본문만 통일하다가 원래의 405 상태나 재시도에 필요한 Allow 헤더를 잃지 않는지 확인한다.
	@Test
	void unsupportedMethodPreservesStatusAndAllowHeader() throws Exception {
		mockMvc.perform(post("/test/success"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(header().string("Allow", containsString("GET")))
				.andExpect(jsonPath("$.code").value("HTTP-405"));
	}

	@Test
	void unsupportedMediaTypePreservesStatus() throws Exception {
		mockMvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("sample"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("HTTP-415"));
	}

	@Test
	void unexpectedErrorDoesNotExposeInternalDetails() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.code").value("COMMON-001"))
				.andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."))
				.andExpect(jsonPath("$.data").value(nullValue()));
	}

	// 운영 API가 아니라 공통 응답의 각 처리 경로를 재현하기 위한 테스트 전용 진입점이다.
	@RestController
	static class TestController {
		@GetMapping("/test/success")
		ResponseEnvelope<Payload> success() {
			return ResponseEnvelope.success(new Payload("sample"));
		}

		@GetMapping("/test/empty")
		ResponseEnvelope<Void> empty() {
			return ResponseEnvelope.success(null);
		}

		@GetMapping("/test/business")
		void business() {
			throw new BusinessException(new ErrorCode() {
				public int getStatus() { return 409; }
				public String getCode() { return "TEST-001"; }
				public String getMessage() { return "이미 처리된 요청입니다."; }
			});
		}

		@PostMapping(value = "/test/body", consumes = MediaType.APPLICATION_JSON_VALUE)
		ResponseEnvelope<Payload> body(@Valid @RequestBody Payload payload) {
			return ResponseEnvelope.success(payload);
		}

		@GetMapping("/test/parameter")
		void parameter(@RequestParam("id") int id) {}

		@GetMapping("/test/header")
		void requiredHeader(@RequestHeader("X-Test") String value) {}

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("internal database connection details");
		}
	}

	record Payload(@NotBlank String name) {}
}
