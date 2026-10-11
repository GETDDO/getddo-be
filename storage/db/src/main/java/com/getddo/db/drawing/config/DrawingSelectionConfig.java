package com.getddo.db.drawing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.getddo.core.drawing.service.DrawingSelectionService;

/** 기존 GD-56 선정기를 결과 확정 서비스에 제공한다. */
@Configuration(proxyBeanMethods = false)
public class DrawingSelectionConfig {
	@Bean
	DrawingSelectionService drawingSelectionService() { return new DrawingSelectionService(); }
}
