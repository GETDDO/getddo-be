package com.getddo.api.drawing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.core.drawing.service.DrawAdminAuthorization;
import com.getddo.core.drawing.service.DrawPublicationQueryService;
import com.getddo.core.drawing.service.DrawPublicationService;
import com.getddo.core.drawing.service.DrawResultConfirmationService;

/** 관리자 확인·이력 조회 서비스의 사용자 조회 의존성을 애플리케이션에서 연결한다. */
@Configuration(proxyBeanMethods = false)
public class DrawPublicationConfig {

	/** 공개 저장소·권한 검사·시각 제공자를 관리자 확인 서비스에 연결한다. */
	@Bean
	DrawResultConfirmationService drawResultConfirmationService(DrawPublicationRepository repository,
			DrawPublicationService publications, DrawAdminAuthorization admins, TimeProvider time) {
		return new DrawResultConfirmationService(repository, publications, admins, time);
	}

	/** 관리자 권한 검사와 공개 변경 이력 조회 의존성을 연결한다. */
	@Bean
	DrawPublicationQueryService drawPublicationQueryService(DrawPublicationService publications,
			DrawPublicationRepository repository, DrawAdminAuthorization admins) {
		return new DrawPublicationQueryService(publications, repository, admins);
	}
}
