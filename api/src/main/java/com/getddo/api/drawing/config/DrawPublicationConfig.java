package com.getddo.api.drawing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.core.drawing.repository.UserDrawResultRepository;
import com.getddo.core.drawing.service.DrawAdminAuthorization;
import com.getddo.core.drawing.service.DrawPublicationQueryService;
import com.getddo.core.drawing.service.DrawPublicationService;
import com.getddo.core.drawing.service.DrawResultConfirmationService;
import com.getddo.core.drawing.service.UserDrawResultService;
import com.getddo.core.event.repository.EventQueryRepository;

/** 확인·공개 이력·사용자 결과 조회 의존성을 애플리케이션에서 연결한다. */
@Configuration(proxyBeanMethods = false)
public class DrawPublicationConfig {

	/** 기존 이벤트 조회와 공개 저장소를 사용자 결과 조회 서비스에 연결한다. */
	@Bean
	UserDrawResultService userDrawResultService(EventQueryRepository events, DrawPublicationRepository publications,
			UserDrawResultRepository results, TimeProvider time) {
		return new UserDrawResultService(events, publications, results, time);
	}

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
