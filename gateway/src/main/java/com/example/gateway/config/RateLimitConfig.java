package com.example.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rate limits are per OAuth2 client (token bucket in Redis, shared by all gateway replicas), so one noisy
 * client can't starve the others. Limits are set on the RequestRateLimiter filter in application.yml.
 */
@Configuration
public class RateLimitConfig {

	@Bean
	KeyResolver clientKeyResolver() {
		return exchange -> exchange.getPrincipal().map(principal -> ClientIds.of(principal));
	}
}
