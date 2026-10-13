package com.getddo.api.drawing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.core.drawing.service.DrawPublicationService;
import com.getddo.core.drawing.service.DrawResultConfirmationService;
import com.getddo.core.drawing.service.DrawPublicationQueryService;
import com.getddo.core.drawing.service.DrawPublicationAdminAuthorization;
import com.getddo.core.user.repository.UserRepository;

/** 관리자 확인·이력 조회 서비스의 사용자 조회 의존성을 애플리케이션에서 연결한다. */
@Configuration(proxyBeanMethods = false)
public class DrawPublicationConfig {
	/** 확인·이력 서비스가 공유할 활성 관리자 권한 검증 빈을 구성한다. */
	@Bean
	DrawPublicationAdminAuthorization drawPublicationAdminAuthorization(UserRepository users) {
		return new DrawPublicationAdminAuthorization(users);
	}

	/** 공개 저장소·권한 검사·시각 제공자를 관리자 확인 서비스에 연결한다. */
	@Bean
	DrawResultConfirmationService drawResultConfirmationService(DrawPublicationRepository repository,
			DrawPublicationService publications, DrawPublicationAdminAuthorization admins, TimeProvider time) {
		return new DrawResultConfirmationService(repository, publications, admins, time);
	}

	/** 관리자 권한 검사와 공개 변경 이력 조회 의존성을 연결한다. */
	@Bean
	DrawPublicationQueryService drawPublicationQueryService(DrawPublicationService publications,
			DrawPublicationRepository repository, DrawPublicationAdminAuthorization admins) {
		return new DrawPublicationQueryService(publications, repository, admins);
	}
}
