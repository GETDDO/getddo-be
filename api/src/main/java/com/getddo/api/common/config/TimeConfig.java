package com.getddo.api.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.getddo.core.time.TimeProvider;

/**
 * 공통 시계와 시간 처리 객체를 Spring Bean으로 등록한다.
 *
 * <p>Bean 간 의존성은 메서드 인자로 주입받으므로 Bean 메서드 호출을
 * 가로채는 설정 클래스 프록시를 사용하지 않는다.</p>
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

	/**
	 * UTC 시간대를 사용하는 시스템 시계를 등록한다.
	 *
	 * @return 현재 시각 조회에 사용할 UTC 시계
	 */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * Spring이 선택한 시계를 사용하는 공통 시간 처리 객체를 등록한다.
	 *
	 * @param clock Spring이 주입한 시계
	 * @return 주입된 시계로 현재 시각을 조회하는 시간 처리 객체
	 */
	@Bean
	public TimeProvider timeProvider(Clock clock) {
		return new TimeProvider(clock);
	}
}
