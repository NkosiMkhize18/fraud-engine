package com.example.gateway.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

class ClientIdCaptureFilterTest {

	private final ClientIdCaptureFilter filter = new ClientIdCaptureFilter();

	@Test
	void recordsTheAuthenticatedClientForTheAccessLog() {
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/assessments"));

		filter.filter(exchange, ex -> Mono.empty())
				.contextWrite(ReactiveSecurityContextHolder
						.withAuthentication(new TestingAuthenticationToken("assessment-reader", null)))
				.block();

		assertThat((String) exchange.getAttribute(ClientIdCaptureFilter.CLIENT_ID_ATTRIBUTE))
				.isEqualTo("assessment-reader");
	}

	@Test
	void anonymousRequestsLeaveNoClientId() {
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/assessments"));

		filter.filter(exchange, ex -> Mono.empty()).block();

		assertThat((Object) exchange.getAttribute(ClientIdCaptureFilter.CLIENT_ID_ATTRIBUTE)).isNull();
	}
}
