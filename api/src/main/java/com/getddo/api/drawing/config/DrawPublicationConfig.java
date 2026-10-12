package com.getddo.api.drawing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.core.drawing.service.DrawPublicationService;
import com.getddo.core.drawing.service.DrawResultConfirmationService;
import com.getddo.core.drawing.service.DrawPublicationQueryService;
import com.getddo.core.drawing.service.DrawPublicationAdminAuthorization;
import com.getddo.core.drawing.service.DrawAdminAuthorization;

/** 관리자 확인·이력 조회 서비스의 사용자 조회 의존성을 애플리케이션에서 연결한다. */
@Configuration(proxyBeanMethods = false)
public class DrawPublicationConfig {
	@Bean
	DrawPublicationAdminAuthorization drawPublicationAdminAuthorization(DrawAdminAuthorization admins) {
		return new DrawPublicationAdminAuthorization(admins);
	}

	@Bean
	DrawResultConfirmationService drawResultConfirmationService(DrawPublicationRepository repository,
			DrawPublicationService publications, DrawPublicationAdminAuthorization admins, TimeProvider time) {
		return new DrawResultConfirmationService(repository, publications, admins, time);
	}

	@Bean
	DrawPublicationQueryService drawPublicationQueryService(DrawPublicationService publications,
			DrawPublicationRepository repository, DrawPublicationAdminAuthorization admins) {
		return new DrawPublicationQueryService(publications, repository, admins);
	}
}
