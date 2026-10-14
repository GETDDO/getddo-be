package com.getddo.api.common.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.getddo.api.common.context.CurrentUserArgumentResolver;

/** 사용자 파라미터 해석기를 MVC에 등록한다. */
@Configuration
public class UserContextWebConfig implements WebMvcConfigurer {

	private final CurrentUserArgumentResolver currentUserArgumentResolver;

	public UserContextWebConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
		this.currentUserArgumentResolver = currentUserArgumentResolver;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(currentUserArgumentResolver);
	}
}
