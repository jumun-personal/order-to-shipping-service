package com.jumunhasyeotjo.order_to_shipping.common.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Logger;

@Configuration
@EnableFeignClients(basePackages = "com.jumunhasyeotjo.order_to_shipping", defaultConfiguration = AutoTracingConfig.class)
public class OpenFeignConfig {
	@Bean
	Logger.Level feignLoggerLevel() {
		// FULL : 요청/응답 헤더 + 바디 + 메타데이터 전체 출력
		// BASIC : 요청 메서드, URL, 응답 상태 코드, 실행시간
		// HEADERS : BASIC + 요청/응답 헤더
		// NONE : 로깅 없음
		return Logger.Level.BASIC;
	}
}
