package com.getddo.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;

// 다른 모듈의 컴포넌트와 JPA 자동 설정 대상도 검색한다.
@AutoConfigurationPackage(basePackages = "com.getddo")
@SpringBootApplication(scanBasePackages = "com.getddo")
public class GetddoBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(GetddoBeApplication.class, args);
	}

}
