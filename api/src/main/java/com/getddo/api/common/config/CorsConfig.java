package com.getddo.api.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * 업무 API에 허용할 origin·메서드·헤더를 하나의 CORS 필터로 관리한다.
 *
 * <p>브라우저의 사전 요청은 Controller에 도달하기 전에 처리하고,
 * 실제 요청의 사용자 헤더 대조와 업무 권한 검사는 기존 MVC·Service 흐름에 맡긴다.
 * CORS 허용 자체가 호출자의 신원이나 업무 권한을 보장하지는 않는다.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

	/**
	 * Spring의 CORS 처리기를 서블릿 필터로 등록한다.
	 *
	 * <p>유효한 preflight는 필터에서 응답하므로 사용자 헤더 값이나 DB 조회가 필요 없다.
	 * 허용된 실제 요청에는 MVC 실행 전에 CORS 응답 헤더를 추가한다.
	 * 따라서 GlobalExceptionHandler가 반환한 4xx·5xx도 브라우저에서 읽을 수 있다.
	 * 필터 자체의 CORS 거절은 MVC 밖에서 일어나므로 공통 JSON 오류 봉투로 처리되지 않는다.</p>
	 *
	 * @param properties YAML과 환경변수에서 바인딩한 CORS 정책
	 * @return 필터와 실행 순서를 서블릿 컨테이너에 전달하는 등록 정보
	 */
	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilter(CorsProperties properties) {
		// 쿠키 사용 여부와 관계없이 정확한 origin 목록만 허용한다.
		// 잘못된 환경 설정을 첫 요청 때 발견하지 않도록 앱 시작 시 검증한다.
		Assert.isTrue(properties.getAllowedOrigins().stream().noneMatch(origin -> origin.contains("*")),
				"getddo.cors.allowed-origins must contain exact origins without wildcards");

		// 전체 origin·헤더를 허용하는 기본값 도우미를 사용하지 않고 명시한 정책만 연결한다.
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.getAllowedOrigins());
		configuration.setAllowedMethods(properties.getAllowedMethods());
		configuration.setAllowedHeaders(properties.getAllowedHeaders());
		configuration.setExposedHeaders(properties.getExposedHeaders());
		configuration.setAllowCredentials(properties.isAllowCredentials());

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		// 업무 API에만 허용 정책을 부여한다. Swagger 등 다른 경로에 CORS 허용을 확대하지 않는다.
		source.registerCorsConfiguration("/api/v1/**", configuration);
		// 필터와 등록 순서를 하나의 Bean으로 묶어 서블릿 컨테이너에 전달한다.
		FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
		// 필터끼리의 상대 순서를 명시한다. 사용자 검증은 필터 이후의 MVC argument resolver가 담당한다.
		registration.setOrder(0);
		return registration;
	}
}
