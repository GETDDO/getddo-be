package com.getddo.db.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.getddo.core.common.time.TimeProvider;
import com.getddo.core.drawing.repository.DrawPublicationRepository;
import com.getddo.core.drawing.repository.UserDrawResultRepository;
import com.getddo.core.drawing.service.UserDrawResultService;
import com.getddo.core.event.repository.EventQueryRepository;
import com.getddo.db.event.repository.EventQueryRepositoryImpl;

/** 다른 추첨 테스트의 컴포넌트 스캔에 유입되지 않도록 지원 패키지에서 조회 Bean을 연결한다. */
@TestConfiguration(proxyBeanMethods = false)
@Import(EventQueryRepositoryImpl.class)
public class UserDrawResultTestConfiguration {
	@Bean
	UserDrawResultService queries(EventQueryRepository events, DrawPublicationRepository publications,
			UserDrawResultRepository results, TimeProvider time) {
		return new UserDrawResultService(events, publications, results, time);
	}
}
