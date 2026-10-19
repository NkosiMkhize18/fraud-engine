package com.example.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

	@Bean
	KeyResolver clientKeyResolver() {
		return exchange -> exchange.getPrincipal().map(principal -> ClientIds.of(principal));
	}
}
