package com.getddo.api.game.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import com.getddo.api.game.controller.GameResultController;
import com.getddo.api.game.dto.request.GameResultRequest;
import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.game.exception.GameErrorCode;

/** G04에만 1MiB 상한을 적용한다. Content-Length가 없는 요청도 상한+1바이트까지만 읽는다. */
@ControllerAdvice(assignableTypes = GameResultController.class)
public class GameResultBodyAdvice extends RequestBodyAdviceAdapter {
	public static final int MAX_BODY_BYTES = 1_048_576;

	@Override
	public boolean supports(MethodParameter parameter, Type targetType,
			Class<? extends HttpMessageConverter<?>> converterType) {
		return parameter.getParameterType() == GameResultRequest.class;
	}

	@Override
	public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter parameter, Type targetType,
			Class<? extends HttpMessageConverter<?>> converterType) throws IOException {
		if (input.getHeaders().getContentLength() > MAX_BODY_BYTES) throw tooLarge();
		byte[] body = input.getBody().readNBytes(MAX_BODY_BYTES + 1);
		if (body.length > MAX_BODY_BYTES) throw tooLarge();
		return new HttpInputMessage() {
			@Override public InputStream getBody() { return new ByteArrayInputStream(body); }
			@Override public HttpHeaders getHeaders() { return input.getHeaders(); }
		};
	}

	private static BusinessException tooLarge() {
		return new BusinessException(GameErrorCode.RESULT_TOO_LARGE);
	}
}
