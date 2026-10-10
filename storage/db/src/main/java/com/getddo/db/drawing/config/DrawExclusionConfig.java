package com.getddo.db.drawing.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.getddo.core.common.exception.BusinessException;
import com.getddo.core.drawing.exception.DrawingErrorCode;
import com.getddo.core.drawing.repository.DrawExclusionRepository;

/** 확정 제외 저장소 연결 전에는 추첨 후보를 임의로 확정하지 않는다. */
@Configuration(proxyBeanMethods = false)
public class DrawExclusionConfig {
	@Bean
	@ConditionalOnMissingBean(DrawExclusionRepository.class)
	DrawExclusionRepository unavailableDrawExclusionRepository() {
		return eventId -> { throw new BusinessException(DrawingErrorCode.EXCLUSION_UNAVAILABLE); };
	}
}
