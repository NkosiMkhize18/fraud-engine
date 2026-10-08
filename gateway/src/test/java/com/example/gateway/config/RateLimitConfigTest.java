package com.example.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class RateLimitConfigTest {

	private final KeyResolver resolver = new RateLimitConfig().clientKeyResolver();

	private static ServerWebExchange exchangeWith(Principal principal) {
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/assessments"));
		return exchange.mutate().principal(Mono.justOrEmpty(principal)).build();
	}

	@Test
	void limitsAreKeyedByClient() {
		assertThat(resolver.resolve(exchangeWith(new TestingAuthenticationToken("noisy-client", null))).block())
				.isEqualTo("noisy-client");
	}

	@Test
	void anonymousRequestsHaveNoKey() {
		assertThat(resolver.resolve(exchangeWith(null)).blockOptional()).isEmpty();
	}
}
