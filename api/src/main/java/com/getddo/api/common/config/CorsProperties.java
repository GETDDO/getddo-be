package com.getddo.api.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code getddo.cors} 설정을 생성자로 바인딩하는 불변 설정 객체다.
 *
 * <p>기본 정책은 application.yaml에서 관리하고, 프론트 origin은 YAML이 참조하는
 * CORS_ALLOWED_ORIGINS 환경변수로 주입한다. 이 클래스가 .env 파일을 직접 읽지는 않는다.
 * 설정 누락을 임의의 허용 값으로 보충하지 않고 빈 목록으로 보관한다.</p>
 */
@ConfigurationProperties("getddo.cors")
public class CorsProperties {

	/** 프로토콜·호스트·포트로 이루어진 허용 origin 목록. 비어 있으면 다른 origin을 허용하지 않는다. */
	private final List<String> allowedOrigins;
	/** 본 요청에 허용할 HTTP 메서드. 사전 요청의 OPTIONS 자체를 추가할 필요는 없다. */
	private final List<String> allowedMethods;
	/** 브라우저가 서버로 보낼 수 있도록 허용할 요청 헤더 이름이다. */
	private final List<String> allowedHeaders;
	/** 기본 공개 헤더 외에 프론트 JavaScript가 추가로 읽을 수 있는 응답 헤더 이름이다. */
	private final List<String> exposedHeaders;
	/** 쿠키 등 브라우저 자격증명 사용의 허용 여부. 사용자 선택 헤더의 전달 여부와는 별개다. */
	private final boolean allowCredentials;

	public CorsProperties(List<String> allowedOrigins, List<String> allowedMethods,
			List<String> allowedHeaders, List<String> exposedHeaders, boolean allowCredentials) {
		this.allowedOrigins = copyOrEmpty(allowedOrigins);
		this.allowedMethods = copyOrEmpty(allowedMethods);
		this.allowedHeaders = copyOrEmpty(allowedHeaders);
		this.exposedHeaders = copyOrEmpty(exposedHeaders);
		this.allowCredentials = allowCredentials;
	}

	public List<String> getAllowedOrigins() {
		return allowedOrigins;
	}

	public List<String> getAllowedMethods() {
		return allowedMethods;
	}

	public List<String> getAllowedHeaders() {
		return allowedHeaders;
	}

	public List<String> getExposedHeaders() {
		return exposedHeaders;
	}

	public boolean isAllowCredentials() {
		return allowCredentials;
	}

	/** 누락된 목록은 빈 값으로 유지하고, 바인딩 후 외부에서 목록을 변경하지 못하도록 복사한다. */
	private static List<String> copyOrEmpty(List<String> values) {
		return values == null ? List.of() : List.copyOf(values);
	}
}
